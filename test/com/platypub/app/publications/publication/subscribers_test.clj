(ns com.platypub.app.publications.publication.subscribers-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.app.publications.publication.subscribers
             :as
             subscribers]
            [tick.core :as tick]))

(deftest subscribers-page-state-test
  (let [[state] (subscribers/subscribers-page)]
    (is (= {:status 404} (state {} {})))
    (is (= 200
           (:status
            (state {}
                   {:request/publication       {:publication/id (random-uuid)}
                    :request/subscriber-search {:subscriber/search ""}
                    :request/subscribers       []}))))))

(deftest update-search-state-test
  (let [[state] (subscribers/update-search)

        publication-id (random-uuid)

        tab-id (random-uuid)]
    (is (= {:status 404} (state {} {})))
    (let [result
          (state {:biff.datastar/tab-id tab-id}
                 {:request/publication {:publication/id publication-id}

                  :request/subscriber-search
                  {:subscriber/search "person@example.com"}

                  :request/tab {}})]
      (is (= :biff.sqlite.fx/execute
             (get-in result [:_search 0])))
      (is (= {:status 204} (:biff.fx/return result))))))

(deftest toggle-subscriber-state-test
  (let [[state] (subscribers/toggle-subscriber)

        now (tick/instant "2026-01-01T00:00:00Z")

        subscriber-id (random-uuid)]
    (is (= {:status 404} (state {:biff.fx/now now} {})))
    (let [result
          (state {:biff.fx/now now}
                 {:request/subscriber {:subscriber/id     subscriber-id
                                       :subscriber/active true}})]
      (is (= :biff.sqlite.fx/authorized-write (get-in result [:_write 0])))
      (is (= now
             (get-in result
                     [:_write 1 :set :subscriber/unsubscribed-at])))
      (is (= {:status 204} (:biff.fx/return result))))
    (is (= {:status 404}
           (state {:biff.fx/now now}
                  {:request/subscriber
                   {:subscriber/id     subscriber-id
                    :subscriber/active false}})))))
