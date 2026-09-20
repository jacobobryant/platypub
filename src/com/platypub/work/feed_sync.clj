(ns com.platypub.work.feed-sync
  (:require [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.feed :as feed]))

(defpipeline feed-sync-consumer
  (concat
   [(fn [{:biff.background/keys [job]}]
      [:biff.graph.fx/query
       {:feed/id (:feed-id job)}
       [:feed/id :feed/url]])

    (fn [_ctx result]
      (if (:feed/id result)
        {:url (:feed/url result), :data {:feed result}}
        {:biff.fx/return nil}))]

   feed/sync-fns

   [(fn [_ctx {:keys [data] :as sync-result}]
      (if (:success sync-result)
        {:publications [:biff.graph.fx/query
                        {:feed/id (get-in data [:feed :feed/id])}
                        [{:feed/publications [:publication/id]}]]}
        {:biff.fx/return nil}))

    (fn [_ctx {:keys [publications]}]
      [:biff.background.fx/submit-jobs
       :platypub/send-readiness
       (mapv #(hash-map :publication-id (:publication/id %))
             (:feed/publications publications))])]))

(def module
  {:biff.background/queues
   {:platypub/feed-sync {:consumer feed-sync-consumer :n-threads 4}}})
