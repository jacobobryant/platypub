(ns com.platypub.app.confirm-subscription
  (:require [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.email :as email]
            [com.platypub.lib.text :as text]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]
            [tick.core :as tick]))

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
      {:token token

       :result
       [:biff.graph.fx/query
        {:subscriber/confirmation-token token}
        [:subscriber/id
         :subscriber/publication-id
         :subscriber/email
         :subscriber/confirmation-triggered-at
         {:subscriber/publication
          [:publication/title :publication/welcome-html]}]]}
      {:biff.fx/return (invalid-response)}))

  (fn [{:biff.fx/keys [now]} {:keys [result]}]
    (if-let [subscriber (when (:subscriber/id result) result)]
      (if (and (:subscriber/confirmation-triggered-at subscriber)
               (tick/> (tick/>> (:subscriber/confirmation-triggered-at
                                 subscriber)
                                (tick/of-hours 24))
                       now))
        {:subscriber subscriber

         :_write
         [:biff.sqlite.fx/execute
          {:update :subscriber
           :set    {:subscriber/confirmed-at              now
                    :subscriber/confirmation-triggered-at nil
                    :subscriber/confirmation-token        nil}
           :where  [:= :subscriber/id (:subscriber/id subscriber)]}]}
        {:biff.fx/return (invalid-response)})
      {:biff.fx/return (invalid-response)}))

  (fn [ctx {:keys [subscriber]}]
    (let [publication (:subscriber/publication subscriber)]
      {:_email
       (when (:mailersend/api-key ctx)
         [:biff.fx/http
          (email/request
           ctx
           {:to      (:subscriber/email subscriber)
            :subject (str "Welcome to " (:publication/title publication))
            :text    (text/html->text (:publication/welcome-html publication))
            :html    (:publication/welcome-html publication)})])

       :biff.fx/return
       (ui/page
        {:ui/title "Subscription confirmed"}
        [:main
         {:class ["mx-auto max-w-xl p-10 text-center"]}
         [:h1 {:class ["text-2xl font-bold"]} "Subscription confirmed"]])})))

(def module
  {:biff.ring/routes [[(routes/confirm-subscription) {:get page}]]})
