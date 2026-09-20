(ns com.platypub.app.publications.publication.send
  (:require [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.middleware :as mid]
            [com.platypub.lib.tab :as tab]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]))

(defpath root-path "")
(defpath confirm-path "/app/publications/:id/send/confirm")

(defpipeline send-page
  [:biff.graph.fx/query
   [{:request/publication
     [:publication/id
      :publication/title
      {:publication/sends [{:send/posts [:post/id]}]}
      {:publication/visible-posts [:post/id :post/title]}]}
    {:request/send-preview
     [:send/subject :send/html {:send/posts [:post/id]}]}]]

  (fn [request result]
    (if-let [publication (:request/publication result)]
      (let [preview (:request/send-preview result)

            used
            (set (map :post/id
                      (mapcat :send/posts
                              (:publication/sends publication))))

            posts
            (remove #(used (:post/id %))
                    (:publication/visible-posts publication))]
        (ui/app-shell
         request
         [:main
          {:class ["mx-auto max-w-3xl p-6"]}
          [:a
           {:href  (routes/publication (:publication/id
                                        publication)),
            :class ["text-blue-700"]} "← Publication"]
          [:h1
           {:class ["my-4 text-3xl font-bold"]}
           "Send newsletter"]
          (if preview
            [:<>
             [:p
              {:class ["my-2"]}
              [:strong "From: "]
              (:publication/title publication)]
             [:p
              {:class ["mb-4"]}
              [:strong "Subject: "]
              (:send/subject preview)]
             [:div {:class ["rounded border p-4"]} (:send/html preview)]
             [:form
              {:data-on:submit "@post(el.dataset.action)",

               :data-action (confirm-path (:publication/id publication)),

               :data-signals__ifmissing (datastar/signals-json {})}
              [:button
               {:class ["mt-4 rounded bg-blue-600 px-4 py-2 text-white"]}
               "Confirm and send"]]]
            [:form
             {:data-on:submit "@post(el.dataset.action)",

              :data-action
              (routes/publication-send (:publication/id publication)),

              :data-signals__ifmissing
              (datastar/signals-json {:send/post-ids []})}
             (for [post posts]
               [:label
                {:class ["mb-2 flex gap-3 rounded border p-4"]}
                [:input
                 {:type      "checkbox",
                  :data-bind (datastar/signal-name :send/post-ids),
                  :value     (:post/id post)}]
                (or (:post/title post) "Untitled post")])
             [:button
              {:class ["mt-4 rounded bg-blue-600 px-4 py-2 text-white"]}
              "Preview"]])]))
      {:status 404})))

(defpipeline preview-send
  [:biff.graph.fx/query
   [{:request/publication [:publication/id]}
    {:request/send-selection [:send/post-ids]}
    {:request/send-posts
     [:post/id
      :post/url
      :post/fetched-at
      :post/published-at
      :post/title
      :post/author-name
      :post/author-url
      :post/author-image-url
      :post/excerpt
      :content/data]}
    {:request/tab [:tab/send-preview]}]]

  (fn [{:keys [biff.datastar/tab-id]} result]
    (if (and (:request/publication result)
             (seq (:request/send-posts result)))
      {:tab-id tab-id
       :result result

       :content
       [:biff.graph.fx/query
        {:send/publication (:request/publication result)
         :send/posts       (:request/send-posts result)}
        [:send/subject :send/html]]}
      {:status 404}))

  (fn [_ctx {:keys [tab-id result content]}]
    (let [publication (:request/publication result)

          posts (:request/send-posts result)]
      {:_preview
       [:biff.sqlite.fx/execute
        (tab/write-statement
         tab-id
         (:request/tab result)
         {:tab/send-preview
          {:publication/id (:publication/id publication)
           :send/subject   (:send/subject content)
           :send/html      (:send/html content)
           :send/post-ids  (mapv :post/id posts)}})]

       :biff.fx/return {:status 204}})))

(defpipeline confirm-send
  [:biff.graph.fx/query
   [{:request/publication
     [:publication/id
      :publication/title
      :publication/intro
      :publication/banner-image-url
      :publication/default-author-name
      :publication/default-author-url
      :publication/default-author-image-url
      :publication/padding-color
      :publication/background-color
      :publication/text-color]}
    {:request/send-posts
     [:post/id
      :post/url
      :post/fetched-at
      :post/published-at
      :post/title
      :post/author-name
      :post/author-url
      :post/author-image-url
      :post/excerpt
      :content/data]}
    {:request/tab [:tab/send-preview]}]]

  (fn [{:biff.fx/keys       [now random-uuid7-seq]
        :biff.datastar/keys [tab-id]}
       result]
    (let [publication (:request/publication result)

          posts (:request/send-posts result)]
      (if (and publication (seq posts))
        {:now    now
         :ids    random-uuid7-seq
         :tab-id tab-id
         :result result

         :content
         [:biff.graph.fx/query
          {:send/publication publication
           :send/posts       posts}
          [:send/subject :send/html :send/text]]}
        {:biff.fx/return {:status 204}})))

  (fn [_ctx {:keys [now ids tab-id result content]}]
    (let [publication (:request/publication result)

          posts (:request/send-posts result)

          send-id (first ids)

          content-id (second ids)

          row-ids (drop 2 ids)

          content
          {:subject (:send/subject content)
           :html    (:send/html content)
           :text    (:send/text content)}]
      {:send-id send-id

       :_write
       [:biff.sqlite.fx/authorized-write-tx
        (into
         [{:insert-into :content
           :values      [{:content/id   content-id
                          :content/data [:lift content]}]}
          {:insert-into :send
           :values      [{:send/id             send-id
                          :send/publication-id (:publication/id publication)
                          :send/started-at     now
                          :send/progress-at    now
                          :send/status         [:lift :send.status/pending]
                          :send/from-name      (:publication/title publication)
                          :send/subject        (:subject content)
                          :send/content-id     content-id

                          :send/provenance [:lift :send.provenance/manual]}]}
          (tab/write-statement tab-id (:request/tab result)
                               {:tab/send-preview nil})]
         (map (fn [post row-id]
                {:insert-into :send-post
                 :values      [{:send-post/id      row-id
                                :send-post/send-id send-id
                                :send-post/post-id (:post/id post)}]})
              posts
              row-ids))]}))

  (fn [_ctx {:keys [send-id]}]
    {:_submit
     [:biff.background.fx/submit-jobs
      :platypub/send
      [{:send-id                  send-id
        :biff.background/priority 0}]]

     :biff.fx/return {:status 204}}))

(def module
  {:biff.ring/routes
   [[(root-path)
     {:middleware [mid/wrap-app-access]}
     [(routes/publication-send)
      {:get send-page, :post preview-send}]
     [(confirm-path) {:post confirm-send}]]]})
