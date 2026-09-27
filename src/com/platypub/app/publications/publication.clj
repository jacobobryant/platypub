(ns com.platypub.app.publications.publication
  (:require [clojure.string :as str]
            [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.middleware :as mid]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]
            [dev.onionpancakes.chassis.core :as chassis]))

(defpath root-path "")
(defpath sync-path "/app/publications/:publication-id/sync")

(defpipeline publication-page
  [:biff.graph.fx/query
   [{:request/pagination [:page/number]}
    {[:? :request/publication]
     [:publication/id
      :publication/title
      [:? :publication/address]
      {:publication/feed [:feed/fetched-at]}
      {:publication/sends [:send/started-at {:send/posts [:post/id]}]}
      :publication/active-subscriber-count
      {:publication/visible-posts [:post/id [:? :post/title]]}]}]]

  (fn [request result]
    (if-let [publication (:request/publication result)]
      (let [page      (max 1
                           (or (get-in result
                                       [:request/pagination :page/number])
                               1))
            all-posts (:publication/visible-posts publication)
            posts     (->> all-posts (drop (* 20 (dec page))) (take 20))

            sent-dates
            (into {}
                  (mapcat
                   (fn [send]
                     (mapv (fn [post]
                             [(:post/id post) (:send/started-at send)])
                           (:send/posts send)))
                   (:publication/sends publication)))

            active (:publication/active-subscriber-count publication)
            unsent (remove #(contains? sent-dates (:post/id %)) all-posts)

            send-unavailable-reason
            (cond
              (empty? unsent) "Add an unsent post to enable sending."

              (zero? active)
              "Add an active, confirmed subscriber to enable sending."

              (str/blank? (:publication/address publication))
              "Add a mailing address in Settings to enable sending.")

            embed (chassis/html
                   [:iframe
                    {:title "Subscribe to publication"
                     :src   (str (:platypub/base-url request)
                                 (routes/subscribe
                                  (:publication/id publication)))}])]
        (ui/app-shell
         request
         [:main
          {:class ["mx-auto w-full max-w-5xl p-6 lg:p-10"]}
          (ui/publication-header publication :posts)
          [:section
           {:class ["my-6 rounded border border-border bg-surface p-5"]}
           [:div
            [:div {:class ["font-semibold"]} "Hosted form"]
            [:a
             {:class ["text-primary hover:underline"]
              :href  (routes/subscribe (:publication/id
                                        publication))}
             (str (:platypub/base-url request)
                  (routes/subscribe (:publication/id
                                     publication)))]]
           [:div {:class ["mt-4 flex items-center justify-between"]}
            [:label {:for "embed-code" :class ["font-semibold"]}
             "Embedded form"]
            [:button
             {:type "button"

              :data-on:click "window.platypubCopyEmbed(el)"

              :class ["text-primary hover:underline"]}
             "Copy"]]
           [:textarea
            {:id       "embed-code"
             :readonly true
             :class    ["mt-1 w-full rounded border border-border p-2"]}
            embed]]
          [:div
           {:class ["mb-5 flex flex-wrap items-center justify-between gap-3"]}
           [:p "Last synced: "
            (ui/timestamp (get-in publication
                                  [:publication/feed :feed/fetched-at]))]
           [:div {:class ["flex gap-2"]}
            [:form
             {:data-on:submit          "@post(el.dataset.action)"
              :data-action             (sync-path (:publication/id publication))
              :data-signals__ifmissing (datastar/signals-json {})}
             [:button {:class ["rounded border border-border px-4 py-2"]}
              "Sync"]]
            [:a
             (if send-unavailable-reason
               {:href             (routes/publication-send
                                   (:publication/id publication))
                :aria-disabled    "true"
                :aria-describedby "send-unavailable-reason"
                :tabindex         -1
                :class            ["rounded border border-border bg-surface"
                                   "pointer-events-none px-4 py-2 text-muted"]}
               {:href  (routes/publication-send (:publication/id publication))
                :class ["rounded bg-primary px-4 py-2 text-white"]})
             "Send"]]]
          (when send-unavailable-reason
            [:p#send-unavailable-reason
             {:class ["mb-5 text-sm text-muted"]}
             send-unavailable-reason])
          [:div
           {:class ["divide-y divide-border rounded border border-border"
                    "bg-surface"]}
           (for [post posts]
             [:article
              {:class ["p-5"]}
              [:h2
               {:class ["text-xl font-semibold"]}
               (or (:post/title post) "Untitled post")]
              [:p
               {:class ["text-sm text-muted"]}
               (if-let [sent-at (sent-dates (:post/id post))]
                 ["Sent " (ui/timestamp sent-at)]
                 "Not sent")]])]
          (when (> (count all-posts) (* page 20))
            [:a
             {:href  (str "?page=" (inc page)),
              :class ["mt-4 inline-block text-primary"]}
             "Next page →"])]))
      {:status 404})))

(defpipeline sync-feed
  [:biff.graph.fx/query
   [{[:? :request/publication] [:publication/id :publication/feed-id]}]]

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
