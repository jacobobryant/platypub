(ns com.platypub.work.feeds
  (:require [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.schedule :as schedule]
            [tick.core :as tick]))

(def sync-delays {0 15, 1 60, 2 180, 3 360, 4 720})

(defpipeline enqueue-feeds
  (fn [_ctx]
    [:biff.graph.fx/query
     [{:global/active-feeds [:feed/id :feed/fetched-at :feed/failed-syncs]}]])

  (fn [{:biff.fx/keys [now]} {:global/keys [active-feeds]}]
    (let [ready (filter
                 (fn [{:feed/keys [fetched-at failed-syncs]}]
                   (let [minutes (get sync-delays failed-syncs 1440)]
                     (not (tick/> (tick/>> fetched-at (tick/of-minutes minutes))
                                  now))))
                 active-feeds)]
      [:biff.background.fx/submit-jobs
       :platypub/feed-sync
       (mapv #(hash-map :feed-id (:feed/id %)) ready)])))

(def module
  {:biff.background/tasks
   [{:schedule (schedule/every (tick/of-minutes 15))
     :task     enqueue-feeds}]})
