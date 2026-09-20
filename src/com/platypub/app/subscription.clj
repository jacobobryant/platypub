(ns com.platypub.app.subscription
  (:require [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.email :as email]
            [com.platypub.lib.subscriber :as subscriber]
            [com.platypub.lib.text :as text]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]
            [dev.onionpancakes.chassis.core :as chassis])
  (:import [java.nio ByteBuffer]
           [java.util Base64]))

(defn- b64-encode [^bytes value]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) value))

(defn- bytes->token [value] (b64-encode value))

(defn- confirmation-token
  [uuid-seq]
  (let [buffer (ByteBuffer/allocate 32)]
    (doseq [uuid (take 2 uuid-seq)]
      (.putLong buffer (.getMostSignificantBits uuid))
      (.putLong buffer (.getLeastSignificantBits uuid)))
    (.array buffer)))

(def publication-attrs
  [:publication/id
   :publication/title
   :publication/description
   :publication/padding-color
   :publication/background-color
   :publication/text-color
   :publication/primary-color
   :publication/banner-image-url
   :publication/require-confirmation
   :publication/welcome-html])

(defn- publication
  [result]
  (when (:publication/title result)
    (select-keys result publication-attrs)))

(defpipeline subscribe-page
  [:biff.graph.fx/query
   publication-attrs]

  (fn [request result]
    (if-let [publication (publication result)]
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
          (:publication/title publication)]
         [:p
          {:class ["my-3"]}
          (:publication/description publication)]
         [:div
          {:data-show "$subscription.submitted"}
          [:h2 {:class ["text-2xl font-bold"]} "Check your inbox"]
          [:p
           {:class ["mt-3"]}
           "Thanks! If confirmation is needed, we've sent you an email."]]
         [:form
          {:data-on:submit
           "$subscription.submitted = true; @post(el.dataset.action)",

           :data-action (routes/subscribe (:publication/id publication)),

           :data-signals__ifmissing
           (datastar/signals-json
            {:subscription/email     ""
             :subscription/submitted false}),

           :data-show "!$subscription.submitted",
           :class     ["flex gap-2"]}
          [:input
           {:data-bind   (datastar/signal-name :subscription/email),
            :type        "email",
            :required    true,
            :placeholder "you@example.com",

            :class ["min-w-0 flex-1 rounded border p-3 text-black"]}]
          [:button
           {:style (str "background:"
                        (:publication/primary-color
                         publication)),
            :class ["rounded px-5 py-3 text-white"]}
           "Subscribe"]]]])
      {:status 404})))

(defpipeline submit-subscription
  [:biff.graph.fx/query
   [:publication/id
    :publication/title
    :publication/require-confirmation
    :publication/welcome-html
    {:request/subscription
     [:subscriber/email
      :subscriber/headers
      :subscriber/form-params
      :subscriber/query-params]}]]

  (fn [_ctx result]
    (let [publication (publication result)

          request-data (:request/subscription result)

          email-address
          (subscriber/normalize-email
           (:subscriber/email request-data))]
      (cond
        (nil? publication) {:biff.fx/return {:status 404}}

        (not (subscriber/valid-email? email-address))
        {:biff.fx/return {:status 204}}

        :else
        {:publication  publication
         :email        email-address
         :request-data (select-keys
                        request-data
                        [:subscriber/headers
                         :subscriber/form-params
                         :subscriber/query-params])

         :existing
         [:biff.graph.fx/query
          {:subscriber/publication-id (:publication/id publication)
           :subscriber/email          email-address}
          [:subscriber/id
           :subscriber/suppressed
           :subscriber/unsubscribed-at
           :subscriber/confirmed-at
           :subscriber/require-confirmation
           :subscriber/active]]})))

  (fn [{:biff.fx/keys [now random-uuid7-seq]}
       {:keys [publication email request-data existing]}]
    (let [previously-active (:subscriber/active existing)

          resubscribe
          (and (:subscriber/unsubscribed-at existing)
               (not (:subscriber/suppressed existing)))

          request-data (update-vals request-data #(when % [:lift %]))

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

       :_write
       (when (or (nil? existing) resubscribe)
         [:biff.sqlite.fx/execute statement])

       :active [:biff.graph.fx/query subscriber [:subscriber/active]]}))

  (fn [{:biff.fx/keys [now random-uuid4-seq]
        :as           ctx}
       {:keys [publication email subscriber previously-active active]}]
    (let [send-confirmation
          (and (not (:subscriber/suppressed subscriber))
               (:subscriber/require-confirmation subscriber)
               (nil? (:subscriber/confirmed-at subscriber)))

          send-welcome (and (not previously-active) (:subscriber/active active))

          token (when send-confirmation (confirmation-token random-uuid4-seq))]
      (cond-> {:biff.fx/return {:status 204}}
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
  {:biff.ring/routes
   [[(routes/subscribe)
     {:get subscribe-page, :post submit-subscription}]]})
