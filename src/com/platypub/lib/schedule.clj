(ns com.platypub.lib.schedule
  (:require [com.biffweb.fx :refer [defpipeline]]
            [tick.core :as tick]))

(defpipeline ^:private starting-at
  (fn [{:biff.fx/keys [now]} duration]
    (tick/>> now duration)))

(defn every
  [duration]
  (fn []
    (iterate #(tick/>> % duration)
             (starting-at {} duration))))
