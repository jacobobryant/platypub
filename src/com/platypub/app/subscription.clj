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
      :publication/padding-color
      :publication/background-color
      :publication/text-color
      :publication/primary-color
      [:? :publication/banner-image-url]
      :publication/require-confirmation
      :publication/welcome-html]}]]

  (fn [request result]
    (if-let [publication (when (get-in result
                                       [:request/subscription-publication
                                        :publication/title])
                           (:request/subscription-publication result))]
      (ui/app-page
       request
       [:main
        {:style (str "background:"
                     (:publication/padding-color
                      publication)),

         :class ["flex min-h-screen items-center justify-center p-6"]}
        [:div
         {:style (str "background:"
                      (:publication/background-color
                       publication)
                      ";color:" (:publication/text-color
                                 publication)),
          :class ["w-full max-w-xl rounded p-8"]}
         (when-let [image (:publication/banner-image-url
                           publication)]
           [:img {:src image, :class ["mb-5 max-w-full"]}])
         [:h1
          {:class ["text-3xl font-bold"]}
          (str "Subscribe to " (:publication/title publication))]
         [:p
          {:class ["my-3"]}
          (:publication/description publication)]
         [:div
          {:data-show "$subscription_submitted"}
          [:h2 {:class ["text-2xl font-bold"]} "Check your inbox"]
          [:p
           {:class ["mt-3"]}
           "We've sent you a confirmation email to "
           [:strong {:data-text "$subscription_email"}]
           "."]]
         [:form
          {:data-on:submit
           "@post(el.dataset.action)",

           :data-action (routes/subscribe (:publication/id publication)),

           :data-signals__ifmissing
           (datastar/signals-json
            {:subscription/email      ""
             :subscription/submitted  false
             :request/turnstile-token ""
             :request/hcaptcha-token  ""}),

           :data-show "!$subscription_submitted",
           :class     ["flex gap-2"]}
          [:input
           {:data-bind   (datastar/signal-name :subscription/email),
            :type        "email",
            :required    true,
            :placeholder "you@example.com",

            :class ["min-w-0 flex-1 rounded border p-3 text-black"]}]
          ;; :biff.auth/skip-captcha is a flag for platypub's signin form. Use a
          ;; separate flag to control the newsletter subscription form.
          (when-not (:biff.auth/skip-captcha request)
            [:div
             [:div {:class         "cf-turnstile"
                    :data-sitekey  (:biff.auth/turnstile-site-key request)
                    :data-callback "platypubTurnstile"}]
             [:div {:id            "hcaptcha-fallback"
                    :class         "h-captcha hidden"
                    :data-sitekey  (:platypub/hcaptcha-site-key request)
                    :data-callback "platypubHcaptcha"}]
             [:input
              {:id        "turnstile-token"
               :type      "hidden"
               :data-bind (datastar/signal-name :request/turnstile-token)}]
             [:input
              {:id        "hcaptcha-token"
               :type      "hidden"
               :data-bind (datastar/signal-name :request/hcaptcha-token)}]
             [:script
              (str
               "window.platypubCaptchaToken=function(id,token){"
               "var input=document.getElementById(id);input.value=token;"
               "input.dispatchEvent(new Event('input',{bubbles:true}));};"
               "window.platypubTurnstile=function(token){"
               "platypubCaptchaToken('turnstile-token',token);};"
               "window.platypubHcaptcha=function(token){"
               "platypubCaptchaToken('hcaptcha-token',token);};")]
             [:script
              {:src     "https://challenges.cloudflare.com/turnstile/v0/api.js"
               :async   true
               :defer   true
               :onerror (str "document.getElementById('hcaptcha-fallback')"
                             ".classList.remove('hidden')")}]
             [:script {:src   "https://js.hcaptcha.com/1/api.js"
                       :async true
                       :defer true}]])
          [:button
           {:style (str "background:"
                        (:publication/primary-color
                         publication)),
            :class ["rounded px-5 py-3 text-white"]}
           "Subscribe"]]]])
      {:status 404})))

(defpipeline submit-subscription
  [:biff.graph.fx/query
   [{[:? :request/subscription-publication]
     [:publication/id
      :publication/title
      :publication/require-confirmation
      :publication/welcome-html]}
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
            (assoc :_captcha
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

  (fn [_ctx {:keys [publication email captcha-valid captcha-provider _captcha]
             :as   state}]
    (if (or captcha-valid (true? (get-in _captcha [:body :success])))
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
         _captcha
         (assoc :captcha-status (:status _captcha)
                :captcha-error-codes (get-in _captcha [:body :error-codes])
                :captcha-exception
                (some-> (:exception _captcha) .getMessage))))))

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
               {:to email

                :subject
                (str "Confirm your subscription to "
                     (:publication/title publication))

                :text
                (str "Confirm: "
                     (:platypub/base-url ctx)
                     (routes/confirm-subscription (bytes->token token)))

                :html
                (chassis/html
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
              {:to      email
               :subject (str "Welcome to " (:publication/title publication))

               :text (text/html->text (:publication/welcome-html publication))

               :html (:publication/welcome-html publication)})])})))))

(def module
  {:biff.ring/base-middleware [wrap-embeddable]

   :biff.ring/routes
   [[(routes/subscribe)
     {:get subscribe-page, :post submit-subscription}]]})
