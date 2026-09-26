(ns com.platypub.work.readiness-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.work.readiness :as readiness]))

(deftest enqueue-publications-states
  (let [[load-publications submit] (readiness/enqueue-publications)]
    (is (= [:biff.graph.fx/query
            [{:global/publications [:publication/id]}]]
           (load-publications {})))
    (is (= [:biff.background.fx/submit-jobs
            :platypub/check-send-readiness
            [{:publication/id 1} {:publication/id 2}]]
           (submit {} {:global/publications [{:publication/id 1}
                                             {:publication/id 2}]})))
    (is (= [:biff.background.fx/submit-jobs
            :platypub/check-send-readiness
            []]
           (submit {} {:global/publications []})))))
