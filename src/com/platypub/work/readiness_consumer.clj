(ns com.platypub.work.readiness-consumer
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [com.biffweb.fx :refer [defpipeline]]
            [tick.core :as tick]))

(defn- send-statements
  [now ids publication posts content]
  (let [[send-id content-id & post-ids] ids]
    {:send-id send-id
     :_write  [:biff.sqlite.fx/execute-tx
               (into [{:insert-into :send
                       :values      [{:send/id send-id

                                      :send/publication-id
                                      (:publication/id publication)

                                      :send/started-at  now
                                      :send/progress-at now

                                      :send/status [:lift :send.status/pending]

                                      :send/from-name
                                      (:publication/title publication)

                                      :send/reply-to
                                      (get-in publication
                                              [:publication/user :user/email])

                                      :send/subject
                                      (:send/subject content)

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
    (let [publication-id (:publication/id job)]
      {:publication [:biff.graph.fx/query
                     {:publication/id publication-id}
                     [:publication/id
                      :publication/title
                      [:? :publication/address]
                      [:? :publication/intro]
                      [:? :publication/email-style]
                      [:? :publication/site-url]
                      [:? :publication/banner-image-url]
                      [:? :publication/default-author-name]
                      [:? :publication/default-author-url]
                      [:? :publication/default-author-image-url]
                      :publication/padding-color
                      :publication/background-color
                      :publication/text-color
                      :publication/primary-color
                      [:? :publication/filter-tag]
                      [:? :publication/remove-tag]
                      [:? :publication/automatic-send-threshold]
                      :publication/active-subscriber-count
                      {:publication/user [:user/email]}
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
               (not (str/blank? (:publication/address publication)))
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
                          [:? :post/url]
                          :post/fetched-at
                          [:? :post/published-at]
                          [:? :post/title]
                          [:? :post/author-name]
                          [:? :post/author-url]
                          [:? :post/author-image-url]
                          [:? :post/excerpt]
                          :post/content-id
                          {:post/content
                           [[:? :content/html] [:? :content/text]]}]}]]}
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
    (let [content-id (second random-uuid7-seq)
          send       (send-statements now
                                      random-uuid7-seq
                                      publication
                                      posts
                                      content)]
      {:biff.fx/seq
       [{:_content [:platypub.fx/put-object
                    {:key          content-id
                     :value        (json/generate-string
                                    {:html (:send/html content)
                                     :text (:send/text content)})
                     :content-type "application/json"}]}
        {:_write (:_write send)}
        {:_submit [:biff.background.fx/submit-jobs
                   :platypub/send
                   [{:send-id (:send-id send)}]]}]})))

(def module
  {:biff.background/queues
   {:platypub/check-send-readiness
    {:consumer readiness-consumer :n-threads 1}}})
