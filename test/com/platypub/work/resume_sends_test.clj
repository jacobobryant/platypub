(ns com.platypub.work.resume-sends-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.work.resume-sends :as resume]))

(deftest resume-sends-states
  (let [[submit] (resume/resume-sends)]
    (is (= [:biff.background.fx/submit-jobs
            :platypub/send
            [{:send-id 1}]]
           (submit {} {:global/stale-sends [{:send/id 1}]})))
    (is (= [:biff.background.fx/submit-jobs :platypub/send []]
           (submit {} {:global/stale-sends []})))))
