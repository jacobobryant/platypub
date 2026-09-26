(ns com.platypub.work.send-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.work.send :as send]
            [tick.core :as tick])
  (:import [java.util UUID]))

(def now (tick/instant "2026-09-13T00:00:00Z"))
(def send-row
  {:send/id             1
   :send/status         :send.status/pending
   :send/publication-id 2
   :send/content-id     3
   :send/started-at     now
   :send/from-name      "News"
   :send/reply-to       "owner@example.com"
   :send/subject        "Subject"})
(def subscriber {:subscriber/id 4 :subscriber/email "reader@example.com"})

(deftest send-consumer-states
  (let [[load-send expand create-deliveries] (send/send-consumer)

        attempt-id (UUID/fromString "00000000-0000-0000-0000-000000000001")]
    (is (= [:biff.graph.fx/query
            {:send/id 1}
            [:send/id
             :send/status
             :send/publication-id
             :send/content-id
             :send/started-at
             :send/from-name
             :send/reply-to
             :send/subject]]
           (load-send {:biff.background/job {:send-id 1}})))
    (testing "missing and completed sends exit"
      (is (= {:biff.fx/return nil} (expand {} {})))
      (is (= {:biff.fx/return nil}
             (expand {} (assoc send-row :send/status :send.status/finished)))))
    (testing "pending sends load delivery data"
      (let [result (expand {} send-row)]
        (is (= :biff.graph.fx/query (first (:send result))))
        (is (= :biff.graph.fx/query (first (:send result))))))
    (let [delivery-data
          {:send {:send/id 1

                  :send/content
                  {:content/id   3
                   :content/html "{{unsubscribe_url}}"
                   :content/text "{{unsubscribe_url}}"}

                  :send/attempts
                  [{:send-attempt/subscriber-id 6}]

                  :send/delivery-subscribers
                  [{:subscriber/id 6 :subscriber/email "attempted@example.com"}
                   subscriber]}}]
      (is (= {:biff.fx/return nil}
             (create-deliveries {:biff.fx/now              now
                                 :biff.fx/random-uuid7-seq [attempt-id]}
                                nil)))
      (let [result
            (create-deliveries {:biff.fx/now                 now
                                :biff.fx/random-uuid7-seq    [attempt-id]
                                :platypub/unsubscribe-secret (delay "secret")
                                :platypub/base-url           "https://example.com"
                                :mailersend/api-key          (delay "secret")}
                               delivery-data)

            effects (vec (:biff.fx/seq result))]
        (is (= :biff.sqlite.fx/execute-tx
               (get-in effects [0 :_delivery 0])))
        (is (= attempt-id
               (get-in effects
                       [0 :_delivery 1 0 :values 0 :send-attempt/id])))
        (is (= [{:send-attempt/id            attempt-id
                 :send-attempt/send-id       1
                 :send-attempt/subscriber-id 4}]
               (get-in effects [0 :_delivery 1 0 :values])))
        (is (re-matches #"https://example\.com/unsubscribe/.+"
                        (get-in effects
                                [1 :_delivery 1 :form-params
                                 0 :personalization 0 :data :unsubscribe_url])))
        (is (= :biff.sqlite.fx/execute
               (get-in effects [2 :_finish 0])))))))
