(ns com.platypub.app.confirm-subscription
  (:require [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.email :as email]
            [com.platypub.lib.text :as text]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]))

(defn- invalid-response
  []
  (ui/page
   {:ui/title "Subscription confirmed"}
   [:main
    {:class ["mx-auto max-w-xl p-10 text-center"]}
    [:h1
     {:class ["text-2xl font-bold"]}
     "This confirmation link is invalid or expired."]]))

(defpipeline page
  [:biff.graph.fx/query
   [{:request/confirmation [:request/token]}]]

  (fn [_ctx result]
    (if-let [token (get-in result [:request/confirmation :request/token])]
      [:biff.graph.fx/query
       {:subscriber/confirmation-token token}
       [[:? :subscriber/id]]]
      {:biff.fx/return (invalid-response)}))

  (fn [_ctx result]
    (if-let [subscriber-id (:subscriber/id result)]
      [:biff.graph.fx/query
       {:subscriber/id subscriber-id}
       [:subscriber/id
        :subscriber/email
        :subscriber/confirmation-token-active
        {:subscriber/publication
         [:publication/title
          [:? :publication/reply-to]
          :publication/welcome-html
          :publication/from-name
          :publication/reply-to-address]}]]
      {:biff.fx/return (invalid-response)}))

  (fn [{:biff.fx/keys [now]} subscriber]
    (if (:subscriber/confirmation-token-active subscriber)
      {:subscriber subscriber

       :_write
       [:biff.sqlite.fx/execute
        {:update :subscriber
         :set    {:subscriber/confirmed-at              now
                  :subscriber/confirmation-triggered-at nil
                  :subscriber/confirmation-token        nil}
         :where  [:= :subscriber/id (:subscriber/id subscriber)]}]}
      {:biff.fx/return (invalid-response)}))

  (fn [_ctx {:keys [subscriber]}]
    {:subscriber subscriber
     :active     [:biff.graph.fx/query
                  {:subscriber/id (:subscriber/id subscriber)}
                  [:subscriber/active]]})

  (fn [ctx {:keys [subscriber active]}]
    (let [publication (:subscriber/publication subscriber)]
      {:_email
       (when (and (:subscriber/active active)
                  (:mailersend/api-key ctx))
         [:biff.fx/http
          (email/request
           ctx
           {:from-name (:publication/from-name publication)
            :reply-to  (:publication/reply-to-address publication)
            :to        (:subscriber/email subscriber)
            :subject   "Welcome"
            :text      (text/html->text
                        (:publication/welcome-html publication))
            :html      (:publication/welcome-html publication)})])

       :biff.fx/return
       (ui/page
        {:ui/title "Subscription confirmed"}
        [:main
         {:class ["mx-auto max-w-xl p-10 text-center"]}
         [:h1 {:class ["text-2xl font-bold"]} "Subscription confirmed"]])})))

(def module
  {:biff.ring/routes [[(routes/confirm-subscription) {:get page}]]})
