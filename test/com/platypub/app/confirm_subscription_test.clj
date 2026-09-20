(ns com.platypub.app.confirm-subscription-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.app.confirm-subscription :as confirmation]
            [tick.core :as tick]))

(deftest page-states-test
  (let [[lookup confirm send-welcome] (confirmation/page)

        now (tick/instant "2026-09-13T00:00:00Z")

        subscriber
        {:subscriber/id             (random-uuid)
         :subscriber/publication-id (random-uuid)
         :subscriber/email          "person@example.com"

         :subscriber/confirmation-triggered-at
         (tick/instant "2026-09-12T01:00:00Z")}

        publication
        {:publication/title        "News"
         :publication/welcome-html "<p>Welcome</p>"}

        token (byte-array [1 2 3])]
    (is (contains? (lookup {} {}) :biff.fx/return))
    (is (= {:token token

            :result
            [:biff.graph.fx/query
             {:subscriber/confirmation-token token}
             [:subscriber/id
              :subscriber/publication-id
              :subscriber/email
              :subscriber/confirmation-triggered-at
              {:subscriber/publication
               [:publication/title :publication/welcome-html]}]]}
           (lookup {} {:request/confirmation {:request/token token}})))
    (testing "expired and missing confirmation links return invalid pages"
      (is (contains?
           (confirm {:biff.fx/now now}
                    {:token  token
                     :result (assoc subscriber
                                    :subscriber/confirmation-triggered-at
                                    (tick/instant
                                     "2026-09-11T00:00:00Z"))})
           :biff.fx/return))
      (is (contains? (confirm {:biff.fx/now now} {:token token :result {}})
                     :biff.fx/return)))
    (let [confirmed
          (confirm {:biff.fx/now now}
                   {:token  token
                    :result subscriber})]
      (is (= :biff.sqlite.fx/execute
             (get-in confirmed [:_write 0])))
      (is (contains? confirmed :subscriber)))
    (let [result
          (send-welcome
           {:mailersend/api-key (delay "secret")}
           {:subscriber
            (assoc subscriber :subscriber/publication publication)})]
      (is (= :biff.fx/http (get-in result [:_email 0])))
      (is (= 200 (:status (:biff.fx/return result)))))
    (let [result (send-welcome
                  {}
                  {:subscriber
                   (assoc subscriber :subscriber/publication publication)})]
      (is (nil? (:_email result)))
      (is (= 200 (:status (:biff.fx/return result)))))))
