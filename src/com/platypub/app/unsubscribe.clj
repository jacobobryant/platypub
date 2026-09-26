(ns com.platypub.app.unsubscribe
  (:require [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]
            [tick.core :as tick]))

(defn- unexpired?
  [now expiration]
  (tick/> (tick/>> (tick/epoch) (tick/of-seconds expiration)) now))

(defpipeline page
  [:biff.graph.fx/query
   [{:request/unsubscribe-claims
     [:subscriber/email :request/expiration :request/token]}]]

  (fn [{:biff.fx/keys [now] :as request} result]
    (if-let [{:subscriber/keys [email] :request/keys [expiration token]}
             (:request/unsubscribe-claims result)]
      (if (unexpired? now expiration)
        (ui/app-page
         request
         [:main
          {:class ["mx-auto max-w-xl p-10 text-center"]}
          [:h1 {:class ["text-2xl font-bold"]} "Unsubscribe"]
          [:p {:class ["my-4"]} (str "Stop emails to " email "?")]
          [:div
           {:data-show "$unsubscribe_submitted"}
           [:h2 "You have been unsubscribed."]]
          [:form
           {:data-on:submit
            "@post(el.dataset.action)",

            :data-action (routes/unsubscribe token),

            :data-signals__ifmissing
            (datastar/signals-json {:unsubscribe/submitted false}),

            :data-show "!$unsubscribe_submitted"}
           [:button
            {:class ["rounded bg-red-600 px-4 py-2 text-white"]}
            "Confirm unsubscribe"]]])
        {:status 410})
      {:status 404})))

(defpipeline unsubscribe
  [:biff.graph.fx/query
   [{:request/unsubscribe-claims [:subscriber/id :request/expiration]}]]

  (fn [{:biff.fx/keys [now]} result]
    (if-let [{:subscriber/keys [id] :request/keys [expiration]}
             (:request/unsubscribe-claims result)]
      (if (unexpired? now expiration)
        {:_write
         [:biff.sqlite.fx/execute
          {:update :subscriber
           :set    {:subscriber/unsubscribed-at now}
           :where  [:= :subscriber/id id]}]

         :biff.fx/return
         (datastar/patch-signals {:unsubscribe/submitted true})}
        {:status 410})
      {:status 404})))

(def module
  {:biff.ring/routes [[(routes/unsubscribe) {:get page, :post unsubscribe}]]})
