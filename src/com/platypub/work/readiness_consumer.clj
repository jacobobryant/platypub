(ns com.platypub.work.readiness-consumer
  (:require [com.biffweb.fx :refer [defpipeline]]
            [tick.core :as tick]))

(defn- send-statements
  [now ids publication posts content]
  (let [send-id      (first ids)
        content-id   (second ids)
        post-ids     (drop 2 ids)
        content-data {:subject (:send/subject content)
                      :html    (:send/html content)
                      :text    (:send/text content)}]
    {:send-id send-id
     :_write  [:biff.sqlite.fx/execute-tx
               (into [{:insert-into :content
                       :values      [{:content/id   content-id
                                      :content/data [:lift content-data]}]}
                      {:insert-into :send
                       :values      [{:send/id send-id

                                      :send/publication-id
                                      (:publication/id publication)

                                      :send/started-at  now
                                      :send/progress-at now

                                      :send/status [:lift :send.status/pending]

                                      :send/from-name
                                      (:publication/title publication)

                                      :send/subject    (:send/subject content)
                                      :send/content-id content-id

                                      :send/provenance
                                      [:lift :send.provenance/automatic]}]}]
                     (map (fn [post row-id]
                            {:insert-into :send-post
                             :values      [{:send-post/id      row-id
                                            :send-post/send-id send-id

                                            :send-post/post-id
                                            (:post/id post)}]})
                          posts
                          post-ids))]}))

(defpipeline readiness-consumer
  (fn [{:biff.background/keys [job]}]
    (let [publication-id (:publication-id job)]
      {:publication [:biff.graph.fx/query
                     {:publication/id publication-id}
                     [:publication/id
                      :publication/title
                      :publication/intro
                      :publication/banner-image-url
                      :publication/default-author-name
                      :publication/default-author-url
                      :publication/default-author-image-url
                      :publication/padding-color
                      :publication/background-color
                      :publication/text-color
                      :publication/filter-tag
                      :publication/remove-tag
                      :publication/automatic-send-threshold
                      :publication/active-subscriber-count
                      {:publication/sends
                       [:send/id :send/status :send/started-at]}]]}))

  (fn [{:biff.fx/keys [now]} {:keys [publication]}]
    (let [active  (:publication/active-subscriber-count publication)
          sends   (:publication/sends publication)
          latest  (first sends)
          pending (some #(= :send.status/pending (:send/status %)) sends)
          elapsed (or (nil? latest)
                      (not (tick/> (tick/>> (:send/started-at latest)
                                            (tick/of-hours 24))
                                   now)))]
      (if (and publication
               (:publication/automatic-send-threshold publication)
               (not pending)
               elapsed
               (pos? active))
        {:publication publication
         :posts       [:biff.graph.fx/query
                       (assoc publication
                              :publication/latest-send-started-at
                              (:send/started-at latest))
                       [{:publication/automatic-posts
                         [:post/id
                          :post/url
                          :post/fetched-at
                          :post/published-at
                          :post/title
                          :post/author-name
                          :post/author-url
                          :post/author-image-url
                          :post/excerpt
                          :content/data]}]]}
        {:biff.fx/return nil})))

  (fn [_ctx {:keys [publication posts]}]
    (let [posts (:publication/automatic-posts posts)]
      (if (seq posts)
        {:publication publication
         :posts       posts

         :content
         [:biff.graph.fx/query
          {:send/publication publication
           :send/posts       posts}
          [:send/subject :send/html :send/text]]}
        {:biff.fx/return nil})))

  (fn [{:biff.fx/keys [now random-uuid7-seq]}
       {:keys [publication posts content]}]
    (send-statements now random-uuid7-seq publication posts content))

  (fn [_ctx {:keys [send-id]}]
    [:biff.background.fx/submit-jobs :platypub/send [{:send-id send-id}]]))

(def module
  {:biff.background/queues
   {:platypub/send-readiness {:consumer readiness-consumer :n-threads 1}}})
