(ns com.platypub.work.readiness
  (:require [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.schedule :as schedule]
            [tick.core :as tick]))

(defpipeline enqueue-publications
  (fn [_ctx]
    [:biff.graph.fx/query
     [{:global/publications [:publication/id]}]])

  (fn [_ctx {:global/keys [publications]}]
    [:biff.background.fx/submit-jobs
     :platypub/send-readiness
     (mapv #(hash-map :publication-id (:publication/id %)) publications)]))

(def module
  {:biff.background/tasks
   [{:schedule (schedule/every (tick/of-minutes 10))
     :task     enqueue-publications}]})
