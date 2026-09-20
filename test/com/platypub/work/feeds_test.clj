(ns com.platypub.work.feeds-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.work.feeds :as feeds]
            [tick.core :as tick]))

(def now (tick/instant "2026-09-13T00:00:00Z"))

(deftest enqueue-feeds-states
  (let [[load-feeds select-ready] (feeds/enqueue-feeds)]
    (is (= [:biff.graph.fx/query
            [{:global/active-feeds
              [:feed/id :feed/fetched-at :feed/failed-syncs]}]]
           (load-feeds {})))
    (is (= [:biff.background.fx/submit-jobs
            :platypub/feed-sync
            [{:feed-id 1} {:feed-id 3}]]
           (select-ready
            {:biff.fx/now now}
            {:global/active-feeds [{:feed/id 1

                                    :feed/fetched-at
                                    (tick/<< now (tick/of-minutes 15))

                                    :feed/failed-syncs 0}
                                   {:feed/id           2
                                    :feed/fetched-at   now
                                    :feed/failed-syncs 0}
                                   {:feed/id 3

                                    :feed/fetched-at
                                    (tick/<< now (tick/of-hours 24))

                                    :feed/failed-syncs 5}]})))
    (is (= [:biff.background.fx/submit-jobs :platypub/feed-sync []]
           (select-ready {:biff.fx/now now} {:global/active-feeds []})))))
