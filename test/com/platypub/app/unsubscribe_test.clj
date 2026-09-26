(ns com.platypub.app.unsubscribe-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.app.unsubscribe :as unsubscribe]
            [tick.core :as tick]))

(deftest page-state-test
  (let [[state] (unsubscribe/page)

        now (tick/instant "2026-09-13T00:00:00Z")]
    (is (= {:status 404} (state {:biff.fx/now now} {})))
    (is (= {:status 410}
           (state {:biff.fx/now now}
                  {:request/unsubscribe-claims
                   {:subscriber/email   "person@example.com"
                    :request/expiration 0}})))
    (is (= 200
           (:status
            (state {:biff.fx/now now}
                   {:request/unsubscribe-claims
                    {:subscriber/email   "person@example.com"
                     :request/expiration 4102444800}}))))))

(deftest unsubscribe-state-test
  (let [[state] (unsubscribe/unsubscribe)

        now (tick/instant "2026-09-13T00:00:00Z")

        subscriber-id (random-uuid)]
    (is (= {:status 404} (state {:biff.fx/now now} {})))
    (is (= {:status 410}
           (state {:biff.fx/now now}
                  {:request/unsubscribe-claims
                   {:subscriber/id      subscriber-id
                    :request/expiration 0}})))
    (let [result
          (state {:biff.fx/now now}
                 {:request/unsubscribe-claims
                  {:subscriber/id      subscriber-id
                   :request/expiration 4102444800}})]
      (is (= :biff.sqlite.fx/execute (get-in result [:_write 0])))
      (is (= 200 (get-in result [:biff.fx/return :status])))
      (is (re-find #"unsubscribe_submitted"
                   (get-in result [:biff.fx/return :body]))))))
