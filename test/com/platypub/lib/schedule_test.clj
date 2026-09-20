(ns com.platypub.lib.schedule-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.lib.schedule :as schedule]
            [tick.core :as tick]))

(deftest starting-at-state-test
  (let [[state] ((ns-resolve 'com.platypub.lib.schedule 'starting-at))

        now (tick/instant "2026-09-14T00:00:00Z")

        duration (tick/of-minutes 15)]
    (is (= (tick/instant "2026-09-14T00:15:00Z")
           (state {:biff.fx/now now} duration)))))

(deftest every-test
  (let [instants ((schedule/every (tick/of-minutes 15)))]
    (is (= (tick/of-minutes 15)
           (tick/between (first instants) (second instants))))))
