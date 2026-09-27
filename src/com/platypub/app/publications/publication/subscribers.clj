(ns com.platypub.app.publications.publication.subscribers
  (:require [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.middleware :as mid]
            [com.platypub.lib.tab :as tab]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]))

(defpath root-path "")
(defpath subscriber-path "/app/subscribers/:subscriber-id")

(defpipeline subscribers-page
  [:biff.graph.fx/query
   [{[:? :request/publication] [:publication/id :publication/title]}
    {:request/subscriber-search [:subscriber/search]}
    {:request/pagination [:page/number :page/limit :page/offset]}
    {:request/subscribers
     [:subscriber/id
      :subscriber/email
      :subscriber/subscribed-at
      [:? :subscriber/unsubscribed-at]
      :subscriber/active]}]]

  (fn [request result]
    (if-let [publication (:request/publication result)]
      (let [search
            (get-in result
                    [:request/subscriber-search :subscriber/search])

            subscribers (:request/subscribers result)]
        (ui/app-shell
         request
         [:main
          {:data-signals__ifmissing
           (datastar/signals-json {:subscriber/activedialog false})

           :class ["mx-auto w-full max-w-5xl p-6"]}
          (ui/publication-header publication :subscribers)
          [:form
           {:data-on:submit "@post(el.dataset.action)",

            :data-action
            (routes/publication-subscribers
             (:publication/id publication))

            :data-signals__ifmissing
            (datastar/signals-json {:subscriber/search search}),

            :class ["mb-4 flex gap-2"]}
           [:input
            {:data-bind   (datastar/signal-name :subscriber/search),
             :placeholder "Search email",
             :class       ["flex-1 rounded border p-2"]}]
           [:button
            {:class ["rounded border px-4"]}
            "Search"]]
          [:table
           {:class ["w-full border-collapse"]}
           [:thead
            [:tr
             [:th {:class ["border p-2 text-left"]} "Email"]
             [:th
              {:class ["border p-2 text-left"]}
              "Subscribed at"]
             [:th
              {:class ["border p-2 text-left"]}
              "Unsubscribed at"]
             [:th]]]
           [:tbody
            (for [subscriber subscribers]
              [:tr
               [:td
                {:class ["border p-2"]}
                (:subscriber/email subscriber)]
               [:td
                {:class ["border p-2"]}
                (ui/timestamp (:subscriber/subscribed-at subscriber))]
               [:td
                {:class ["border p-2"]}
                (ui/timestamp (:subscriber/unsubscribed-at subscriber))]
               [:td
                {:class ["border p-2"]}
                (when (:subscriber/active subscriber)
                  [:div
                   [:details
                    [:summary {:class ["cursor-pointer list-none"]} "⋯"]
                    [:button
                     {:type "button"

                      :data-on:click
                      (str "$subscriber_activedialog = '"
                           (:subscriber/id subscriber)
                           "'")

                      :class ["text-primary"]}
                     "Unsubscribe"]]
                   (ui/modal
                    {:id    (str "unsubscribe-"
                                 (:subscriber/id subscriber))
                     :class ["w-full max-w-md rounded border"
                             "border-border bg-surface p-6 shadow-xl"]}
                    (str "$subscriber_activedialog === '"
                         (:subscriber/id subscriber) "'")
                    "$subscriber_activedialog = false"
                    [:h2 {:class ["text-xl font-semibold"]}
                     "Unsubscribe subscriber?"]
                    [:p {:class ["my-4"]} (:subscriber/email subscriber)]
                    [:div {:class ["flex justify-end gap-2"]}
                     [:button {:type "button"

                               :data-on:click
                               "$subscriber_activedialog = false"

                               :class
                               ["rounded border border-border px-4 py-2"]}
                      "Cancel"]
                     [:form
                      {:data-on:submit          "@post(el.dataset.action)"
                       :data-action             (subscriber-path
                                                 (:subscriber/id subscriber))
                       :data-signals__ifmissing (datastar/signals-json {})}
                      [:button
                       {:class ["rounded bg-primary px-4 py-2 text-white"]}
                       "Unsubscribe"]]])])]])]
           (let [page (or (get-in result [:request/pagination :page/number]) 1)]
             [:nav {:class ["mt-4 flex gap-4"]}
              (when (> page 1)
                [:a {:href (str "?page=" (dec page)) :class ["text-primary"]}
                 "Previous"])
              (when (= 50 (count subscribers))
                [:a {:href (str "?page=" (inc page)) :class ["text-primary"]}
                 "Next"])])]]))
      {:status 404})))

(defpipeline update-search
  [:biff.graph.fx/query
   [{[:? :request/publication] [:publication/id]}
    {:request/subscriber-search [:subscriber/search]}
    {:request/tab
     [{[:? :tab/subscriber-search]
       [:publication/id :subscriber/search]}]}]]

  (fn [{:keys [biff.datastar/tab-id]} result]
    (if-let [publication (:request/publication result)]
      {:_search
       [:biff.sqlite.fx/execute
        (tab/write-statement
         tab-id
         (:request/tab result)
         {:tab/subscriber-search
          {:publication/id    (:publication/id publication)
           :subscriber/search (get-in result
                                      [:request/subscriber-search
                                       :subscriber/search])}})]

       :biff.fx/return {:status 204}}
      {:status 404})))

(defpipeline toggle-subscriber
  [:biff.graph.fx/query
   [{:request/subscriber [:subscriber/id :subscriber/active]}]]

  (fn [{:biff.fx/keys [now]} result]
    (if-let [subscriber (when (get-in result [:request/subscriber
                                              :subscriber/active])
                          (:request/subscriber result))]
      (let [subscriber-id (:subscriber/id subscriber)]
        {:_write
         [:biff.sqlite.fx/authorized-write
          {:update :subscriber,

           :set
           {:subscriber/unsubscribed-at now},

           :where [:= :subscriber/id subscriber-id]}]

         :biff.fx/return {:status 204}})
      {:status 404})))

(def module
  {:biff.ring/routes
   [[(root-path)
     {:middleware [mid/wrap-app-access]}
     [(routes/publication-subscribers)
      {:get subscribers-page, :post update-search}]
     [(subscriber-path) {:post toggle-subscriber}]]]})
