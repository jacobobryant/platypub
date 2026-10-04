(ns com.platypub.app.subscription
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.email :as email]
            [com.platypub.lib.subscriber :as subscriber]
            [com.platypub.lib.text :as text]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]
            [dev.onionpancakes.chassis.core :as chassis])
  (:import [java.nio ByteBuffer]
           [java.nio.charset StandardCharsets]
           [java.util Base64]))

(defn- b64-encode [^bytes value]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) value))

(defn- bytes->token [value] (b64-encode value))

(defn- submitted-response [email-address]
  (datastar/patch-signals {:subscription/email     email-address
                           :subscription/submitted true}))

(defn- failed-subscription
  [email publication-id reason details]
  (log/warn "Subscription rejected"
            (merge {:email          email
                    :publication-id publication-id
                    :reason         reason}
                   details))
  {:biff.fx/return (submitted-response email)})

(defn- json-bytes [value]
  (.getBytes (json/generate-string value) StandardCharsets/UTF_8))

(defn- confirmation-token
  [uuid-seq]
  (let [buffer (ByteBuffer/allocate 32)]
    (doseq [uuid (take 2 uuid-seq)]
      (.putLong buffer (.getMostSignificantBits uuid))
      (.putLong buffer (.getLeastSignificantBits uuid)))
    (.array buffer)))

(defn wrap-embeddable
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if (str/starts-with? (:uri request) "/subscribe/")
        (-> response
            (update :headers dissoc "X-Frame-Options")
            (assoc-in [:headers "Content-Security-Policy"]
                      "frame-ancestors *"))
        response))))

(defpipeline subscribe-page
  [:biff.graph.fx/query
   [{[:? :request/subscription-publication]
     [:publication/id
      :publication/title
      [:? :publication/description]
      [:? :publication/hide-form-title]
      [:? :publication/archive-url]
      [:? :publication/form-placeholder]
      [:? :publication/form-style]
      :publication/padding-color
      :publication/background-color
      :publication/text-color
      :publication/primary-color
      {:publication/feed [:feed/url]}
      :publication/ui-subscribe-form]}]]

  (fn [request result]
    (if-let [publication (when (get-in result
                                       [:request/subscription-publication
                                        :publication/title])
                           (:request/subscription-publication result))]
      (let [embed (= "1" (get-in request [:query-params "embed"]))]
        (ui/page (assoc request
                        :ui/embed embed
                        :ui/title (:publication/title publication))
                 ((:publication/ui-subscribe-form publication)
                  {:embed embed})))
      {:status 404})))

(defpipeline submit-subscription
  [:biff.graph.fx/query
   [{[:? :request/subscription-publication]
     [:publication/id
      :publication/title
      [:? :publication/reply-to]
      :publication/require-confirmation
      :publication/welcome-html
      :publication/from-name
      :publication/reply-to-address]}
    {:request/subscription
     [:subscriber/email
      [:? :request/turnstile-token]
      [:? :request/hcaptcha-token]]}]]

  (fn [{:keys [biff.auth/skip-captcha
               biff.auth/turnstile-secret
               platypub/hcaptcha-secret-key]
        :as   ctx}
       result]
    (let [publication
          (when (get-in result
                        [:request/subscription-publication :publication/title])
            (:request/subscription-publication result))

          email-address
          (subscriber/normalize-email
           (get-in result [:request/subscription :subscriber/email]))

          request-data
          (assoc (:request/subscription result)
                 :subscriber/headers (:headers ctx)
                 :subscriber/form-params
                 (or (:form-params ctx) {:email email-address})
                 :subscriber/query-params (:query-params ctx))]
      (cond
        (nil? publication)
        (do
          (failed-subscription email-address
                               (get-in ctx [:path-params :publication-id])
                               :publication-not-found
                               {})
          {:biff.fx/return {:status 404}})

        (not (subscriber/valid-email? email-address))
        (failed-subscription email-address (:publication/id publication)
                             :invalid-email {})

        :else
        (let [turnstile (not-empty (:request/turnstile-token request-data))
              hcaptcha  (not-empty (:request/hcaptcha-token request-data))
              provider  (cond turnstile :turnstile hcaptcha :hcaptcha)]
          (cond->
           {:publication      publication
            :email            email-address
            :captcha-valid    skip-captcha
            :captcha-provider provider
            :request-data     (select-keys
                               request-data
                               [:subscriber/headers
                                :subscriber/form-params
                                :subscriber/query-params])}
            (and (not skip-captcha) provider)
            (assoc :captcha-response
                   [:biff.fx/http
                    {:method           :post
                     :url              (if (= provider :turnstile)
                                         (str "https://challenges.cloudflare.com/"
                                              "turnstile/v0/siteverify")
                                         "https://api.hcaptcha.com/siteverify")
                     :form-params      {:secret
                                        (force
                                         (if (= provider :turnstile)
                                           turnstile-secret
                                           hcaptcha-secret-key))

                                        :response (or turnstile hcaptcha)}
                     :as               :json
                     :coerce           :always
                     :throw-exceptions false}]))))))

  (fn [_ctx {:keys [publication email captcha-valid captcha-provider
                    captcha-response]
             :as   state}]
    (if (or captcha-valid (true? (get-in captcha-response [:body :success])))
      (assoc state
             :existing
             [:biff.graph.fx/query
              {:subscriber/publication-id (:publication/id publication)
               :subscriber/email          email}
              [[:? :subscriber/id]
               [:? :subscriber/suppressed]
               [:? :subscriber/unsubscribed-at]
               [:? :subscriber/confirmed-at]
               [:? :subscriber/require-confirmation]
               [:? :subscriber/active]]])
      (failed-subscription
       email (:publication/id publication)
       (if captcha-provider :captcha-verification-failed :captcha-token-missing)
       (cond-> {:captcha-provider captcha-provider}
         captcha-response
         (assoc :captcha-status (:status captcha-response)
                :captcha-error-codes
                (get-in captcha-response [:body :error-codes])
                :captcha-exception
                (some-> (:exception captcha-response) .getMessage))))))

  (fn [{:biff.fx/keys [now random-uuid7-seq]}
       {:keys [publication email request-data existing]}]
    (let [existing          (when (:subscriber/id existing) existing)
          previously-active (:subscriber/active existing)

          resubscribe
          (and (:subscriber/unsubscribed-at existing)
               (not (:subscriber/suppressed existing)))

          request-data
          (into {}
                (keep (fn [[field value]]
                        (when (some? value)
                          [field (json-bytes value)])))
                request-data)

          subscriber
          (cond
            (nil? existing)
            (merge {:subscriber/id (first random-uuid7-seq)

                    :subscriber/email email

                    :subscriber/publication-id (:publication/id publication)

                    :subscriber/subscribed-at now

                    :subscriber/require-confirmation
                    (:publication/require-confirmation
                     publication)}
                   request-data)

            resubscribe
            (assoc existing :subscriber/subscribed-at now
                   :subscriber/unsubscribed-at nil
                   :subscriber/confirmed-at nil
                   :subscriber/require-confirmation true)

            :else existing)

          statement
          (if existing
            {:update :subscriber

             :set
             (select-keys
              subscriber
              [:subscriber/subscribed-at
               :subscriber/unsubscribed-at
               :subscriber/confirmed-at
               :subscriber/require-confirmation])

             :where [:=
                     :subscriber/id
                     (:subscriber/id subscriber)]}
            {:insert-into :subscriber
             :values      [subscriber]})]
      {:publication       publication
       :email             email
       :subscriber        subscriber
       :previously-active previously-active
       :wrote             (or (nil? existing) resubscribe)

       :biff.fx/seq
       [{:_write (when (or (nil? existing) resubscribe)
                   [:biff.sqlite.fx/execute statement])}
        {:active [:biff.graph.fx/query subscriber [:subscriber/active]]}]}))

  (fn [{:biff.fx/keys [now random-uuid4-seq]
        :as           ctx}
       {:keys [publication email subscriber previously-active active]}]
    (let [send-confirmation
          (and (not (:subscriber/suppressed subscriber))
               (:subscriber/require-confirmation subscriber)
               (nil? (:subscriber/confirmed-at subscriber)))

          send-welcome (and (not previously-active) (:subscriber/active active))

          token (when send-confirmation (confirmation-token random-uuid4-seq))]
      (cond-> {:biff.fx/return (submitted-response email)}
        send-confirmation
        (assoc
         :biff.fx/seq
         [{:_write
           [:biff.sqlite.fx/execute
            {:update :subscriber

             :set
             {:subscriber/confirmation-token        token
              :subscriber/confirmation-triggered-at now}

             :where [:= :subscriber/id (:subscriber/id subscriber)]}]}
          {:_email
           (when (:mailersend/api-key ctx)
             [:biff.fx/http
              (email/request
               ctx
               {:from-name (:publication/from-name publication)
                :reply-to  (:publication/reply-to-address publication)
                :to        email
                :subject   "Confirm your subscription"
                :text      (str "Confirm: "
                                (:platypub/base-url ctx)
                                (routes/confirm-subscription
                                 (bytes->token token)))
                :html      (chassis/html
                            [:p
                             [:a
                              {:href (str (:platypub/base-url ctx)
                                          (routes/confirm-subscription
                                           (bytes->token token)))}
                              "Confirm subscription"]])})])}])

        send-welcome
        (update
         :biff.fx/seq
         (fnil conj [])
         {:_email
          (when (:mailersend/api-key ctx)
            [:biff.fx/http
             (email/request
              ctx
              {:from-name (:publication/from-name publication)
               :reply-to  (:publication/reply-to-address publication)
               :to        email
               :subject   "Welcome"
               :text      (text/html->text
                           (:publication/welcome-html publication))
               :html      (:publication/welcome-html publication)})])})))))

(def module
  {:biff.ring/base-middleware [wrap-embeddable]

   :biff.ring/routes
   [[(routes/subscribe)
     {:get subscribe-page, :post submit-subscription}]]})
