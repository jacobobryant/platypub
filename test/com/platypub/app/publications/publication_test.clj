(ns com.platypub.app.publications.publication-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.app.publications.publication :as publication]))

(deftest publication-page-state-test
  (let [[state] (publication/publication-page)

        result
        {:request/pagination {:page/number 1}

         :request/publication
         {:publication/id                      (random-uuid)
          :publication/title                   "News"
          :publication/sends                   []
          :publication/active-subscriber-count 0
          :publication/visible-posts           []}}]
    (is (= 200 (:status (state {} result))))
    (is (= {:status 404} (state {} {})))))

(deftest sync-feed-state-test
  (let [[state] (publication/sync-feed)

        feed-id (random-uuid)]
    (is (= {:status 404} (state {} {})))
    (is (= {:status 204}
           (:biff.fx/return
            (state {}
                   {:request/publication
                    {:publication/id      (random-uuid)
                     :publication/feed-id feed-id}}))))))
