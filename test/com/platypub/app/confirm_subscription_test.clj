(ns com.platypub.app.confirm-subscription-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.app.confirm-subscription :as confirmation]
            [tick.core :as tick]))

(deftest page-states-test
  (let [[lookup load-subscriber confirm load-active send-welcome]
        (confirmation/page)

        now (tick/instant "2026-09-13T00:00:00Z")

        subscriber
        {:subscriber/id                        (random-uuid)
         :subscriber/publication-id            (random-uuid)
         :subscriber/email                     "person@example.com"
         :subscriber/confirmation-token-active true}

        publication
        {:publication/title        "News"
         :publication/welcome-html "<p>Welcome</p>"}

        token (byte-array [1 2 3])]
    (is (contains? (lookup {} {}) :biff.fx/return))
    (is (= [:biff.graph.fx/query
            {:subscriber/confirmation-token token}
            [[:? :subscriber/id]]]
           (lookup {} {:request/confirmation {:request/token token}})))
    (testing "expired and missing confirmation links return invalid pages"
      (is (contains? (load-subscriber {} {}) :biff.fx/return))
      (is (contains?
           (confirm {:biff.fx/now now}
                    (assoc subscriber :subscriber/confirmation-token-active
                           false))
           :biff.fx/return)))
    (is (= :biff.graph.fx/query
           (first
            (load-subscriber
             {}
             {:subscriber/id (:subscriber/id subscriber)}))))
    (let [confirmed
          (confirm {:biff.fx/now now} subscriber)]
      (is (= :biff.sqlite.fx/execute
             (get-in confirmed [:_write 0])))
      (is (contains? confirmed :subscriber))
      (is (= :biff.graph.fx/query
             (get-in (load-active {} confirmed) [:active 0]))))
    (let [result
          (send-welcome
           {:mailersend/api-key (delay "secret")}
           {:subscriber (assoc subscriber :subscriber/publication publication)
            :active     {:subscriber/active true}})]
      (is (= :biff.fx/http (get-in result [:_email 0])))
      (is (= 200 (:status (:biff.fx/return result)))))
    (let [result (send-welcome
                  {}
                  {:subscriber (assoc subscriber
                                      :subscriber/publication publication)
                   :active     {:subscriber/active true}})]
      (is (nil? (:_email result)))
      (is (= 200 (:status (:biff.fx/return result)))))))
