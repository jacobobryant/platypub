(ns com.platypub.app.publications.archived
  (:require [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.middleware :as mid]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]))

(defpipeline archived-page
  [:biff.graph.fx/query
   [{:request/user
     [{:user/archived-publications
       [:publication/id :publication/title :publication/archived-at]}]}]]

  (fn [request result]
    (let [publications (get-in result
                               [:request/user :user/archived-publications])]
      (ui/app-shell
       request
       [:main {:class ["mx-auto w-full max-w-5xl p-6 lg:p-10"]}
        [:a {:href (routes/app), :class ["text-primary hover:underline"]}
         "← Publications"]
        [:h1 {:class ["my-5 text-3xl font-bold"]} "Archived Publications"]
        [:div {:class ["grid gap-4"]}
         (for [publication publications]
           [:div {:class ["flex items-center justify-between rounded border"
                          "border-border bg-surface p-5"]}
            [:h2 {:class ["text-xl font-semibold"]}
             (:publication/title publication)]
            [:details {:class ["relative"]}
             [:summary {:class ["cursor-pointer list-none rounded border"
                                "border-border px-3 py-1"]}
              "⋯"]
             [:form
              {:data-on:submit          "@post(el.dataset.action)"
               :data-action             (routes/unarchive-publication
                                         (:publication/id publication))
               :data-signals__ifmissing (datastar/signals-json {})
               :class                   ["absolute right-0 z-10 mt-2 rounded"
                                         "border border-border bg-surface p-2"
                                         "shadow"]}
              [:button {:class ["whitespace-nowrap px-3 py-1 text-primary"]}
               "Unarchive"]]]])]]))))

(defpipeline archive-publication
  [:biff.graph.fx/query
   [{[:? :request/publication] [:publication/id]}]]

  (fn [{:biff.fx/keys [now]} result]
    (if-let [publication (:request/publication result)]
      {:_write
       [:biff.sqlite.fx/authorized-write
        {:update :publication
         :set    {:publication/archived-at now}
         :where  [:= :publication/id (:publication/id publication)]}]

       :biff.fx/return {:status 204}}
      {:status 404})))

(defpipeline unarchive-publication
  [:biff.graph.fx/query
   [{[:? :request/archived-publication] [:publication/id]}]]

  (fn [_ctx result]
    (if-let [publication (:request/archived-publication result)]
      {:_write
       [:biff.sqlite.fx/authorized-write
        {:update :publication
         :set    {:publication/archived-at nil}
         :where  [:= :publication/id (:publication/id publication)]}]

       :biff.fx/return {:status 204}}
      {:status 404})))

(def module
  {:biff.ring/routes
   [[""
     {:middleware [mid/wrap-app-access]}
     [(routes/archived-publications) {:get archived-page}]
     [(routes/archive-publication) {:post archive-publication}]
     [(routes/unarchive-publication) {:post unarchive-publication}]]]})
