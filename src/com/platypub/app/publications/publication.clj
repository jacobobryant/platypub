(ns com.platypub.app.publications.publication
  (:require [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.middleware :as mid]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]
            [dev.onionpancakes.chassis.core :as chassis]))

(defpath root-path "")
(defpath sync-path "/app/publications/:id/sync")

(defpipeline publication-page
  [:biff.graph.fx/query
   [{:request/pagination [:page/number]}
    {:request/publication
     [:publication/id
      :publication/title
      {:publication/sends [:send/started-at {:send/posts [:post/id]}]}
      :publication/active-subscriber-count
      {:publication/visible-posts [:post/id :post/title]}]}]]

  (fn [request result]
    (if-let [publication (:request/publication result)]
      (let
       [page (max 1 (or (get-in result [:request/pagination :page/number]) 1))

        all-posts (:publication/visible-posts publication)

        posts (->> all-posts (drop (* 20 (dec page))) (take 20))

        sent-dates
        (into {}
              (mapcat
               (fn [send]
                 (map (fn [post]
                        [(:post/id post) (:send/started-at send)])
                      (:send/posts send)))
               (:publication/sends publication)))

        active (:publication/active-subscriber-count publication)

        unsent (remove #(contains? sent-dates (:post/id %)) all-posts)]
        (ui/app-shell
         request
         [:main
          {:class ["mx-auto w-full max-w-5xl p-6"]}
          [:a
           {:href (routes/app), :class ["text-blue-700"]}
           "← Publications"]
          [:div
           {:class ["mt-4 flex flex-wrap items-center justify-between gap-3"]}
           [:h1
            {:class ["text-3xl font-bold"]}
            (:publication/title publication)]
           [:div
            {:class ["flex gap-2"]}
            [:form
             {:data-on:submit          "@post(el.dataset.action)"
              :data-action             (sync-path (:publication/id publication))
              :data-signals__ifmissing (datastar/signals-json {})}
             [:button
              {:class ["rounded border px-4 py-2"]}
              "Sync feed"]]
            [:a
             {:href  (routes/publication-subscribers
                      (:publication/id publication)),
              :class ["rounded border px-4 py-2"]}
             (str "Subscribers (" active ")")]
            [:a
             {:href  (routes/publication-settings
                      (:publication/id publication)),
              :class ["rounded border px-4 py-2"]}
             "Settings"]
            [:a
             (cond->
              {:href (routes/publication-send
                      (:publication/id publication)),

               :class ["rounded bg-blue-600 px-4 py-2 text-white"]}
               (or (empty? unsent) (zero? active))
               (assoc :aria-disabled
                      "true" :class
                      ["pointer-events-none rounded bg-gray-300"
                       "px-4 py-2 text-gray-600"])) "Send"]]]
          [:section
           {:class ["my-6 rounded bg-gray-50 p-4"]}
           [:div
            [:strong "Hosted form: "]
            [:a
             {:class ["text-blue-700"],
              :href  (routes/subscribe (:publication/id
                                        publication))}
             (str (:platypub/base-url request)
                  (routes/subscribe (:publication/id
                                     publication)))]]
           [:label
            {:class ["mt-3 block font-semibold"]}
            "Embed code"]
           [:textarea
            {:readonly true,
             :class    ["mt-1 w-full rounded border p-2"]}
            (chassis/html
             [:iframe
              {:src (str (:platypub/base-url request)
                         (routes/subscribe (:publication/id publication)))}])]]
          [:div
           {:class ["divide-y rounded border"]}
           (for [post posts]
             [:article
              {:class ["p-5"]}
              [:h2
               {:class ["text-xl font-semibold"]}
               (or (:post/title post) "Untitled post")]
              [:p
               {:class ["text-sm text-gray-500"]}
               (if-let [sent-at (sent-dates (:post/id post))]
                 (str "Sent " sent-at)
                 "Not sent")]])]
          (when (> (count all-posts) (* page 20))
            [:a
             {:href  (str "?page=" (inc page)),
              :class ["mt-4 inline-block text-blue-700"]}
             "Next page →"])]))
      {:status 404})))

(defpipeline sync-feed
  [:biff.graph.fx/query
   [{:request/publication [:publication/id :publication/feed-id]}]]

  (fn [_ctx result]
    (if-let [publication (:request/publication result)]
      {:_submit
       [:biff.background.fx/submit-jobs
        :platypub/feed-sync
        [{:feed-id                  (:publication/feed-id publication)
          :biff.background/priority 0}]]

       :biff.fx/return {:status 204}}
      {:status 404})))

(def module
  {:biff.ring/routes
   [[(root-path)
     {:middleware [mid/wrap-app-access]}
     [(routes/publication) {:get publication-page}]
     [(sync-path) {:post sync-feed}]]]})
