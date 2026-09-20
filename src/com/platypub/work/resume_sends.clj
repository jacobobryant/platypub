(ns com.platypub.work.resume-sends
  (:require [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.schedule :as schedule]
            [tick.core :as tick]))

(defpipeline resume-sends
  [:biff.graph.fx/query
   [{:global/stale-sends [:send/id]}]]

  (fn [_ctx {:global/keys [stale-sends]}]
    [:biff.background.fx/submit-jobs
     :platypub/send
     (mapv #(hash-map :send-id (:send/id %)) stale-sends)]))

(def module
  {:biff.background/tasks
   [{:schedule (schedule/every (tick/of-minutes 10))
     :task     resume-sends}]})
