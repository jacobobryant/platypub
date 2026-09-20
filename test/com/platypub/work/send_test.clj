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
   :send/started-at     now})
(def subscriber {:subscriber/id 4 :subscriber/email "reader@example.com"})

(deftest send-consumer-states
  (let [[load-send expand hydrate create-deliveries] (send/send-consumer)

        attempt-id (UUID/fromString "00000000-0000-0000-0000-000000000001")]
    (is (= [:biff.graph.fx/query
            {:send/id 1}
            [:send/id
             :send/status
             :send/publication-id
             :send/content-id
             :send/started-at]]
           (load-send {:biff.background/job {:send-id 1}})))
    (testing "missing and completed sends exit"
      (is (= {:biff.fx/return nil} (expand {} {})))
      (is (= {:biff.fx/return nil}
             (expand {} (assoc send-row :send/status :send.status/finished)))))
    (testing "pending sends load delivery data"
      (let [result (expand {} send-row)]
        (is (= :biff.graph.fx/query (first (:send result))))
        (is (= :biff.graph.fx/query (first (:send result))))))
    (is (= {:biff.fx/return nil} (hydrate {} nil)))
    (let [hydrated (hydrate {}
                            {:send {:send/id 1

                                    :send/publication
                                    {:publication/id      2
                                     :publication/title   "News"
                                     :publication/user-id 5

                                     :publication/user
                                     {:user/id    5
                                      :user/email "owner@example.com"}}

                                    :send/content
                                    {:content/id 3

                                     :content/data
                                     {:html "{{unsubscribe_url}}"
                                      :text "{{unsubscribe_url}}"}}

                                    :send/attempts
                                    [{:send-attempt/subscriber-id 6}]

                                    :send/delivery-subscribers [subscriber]}})]
      (is (= #{6} (:attempted hydrated)))
      (is (= [subscriber] (:subscribers hydrated)))
      (is (= {:user/id 5 :user/email "owner@example.com"}
             (:owner hydrated)))
      (is (= {:biff.fx/return nil}
             (create-deliveries {:biff.fx/now              now
                                 :biff.fx/random-uuid7-seq [attempt-id]}
                                nil)))
      (let [result
            (create-deliveries {:biff.fx/now                 now
                                :biff.fx/random-uuid7-seq    [attempt-id]
                                :platypub/unsubscribe-secret (delay "secret")
                                :platypub/base-url           "https://example.com"}
                               (assoc hydrated
                                      :owner
                                      {:user/id    5
                                       :user/email "owner@example.com"}))

            effects (vec (:biff.fx/seq result))]
        (is (= :biff.sqlite.fx/execute-tx
               (ffirst effects)))
        (is (= attempt-id
               (get-in effects [0 1 0 :values 0 :send-attempt/id])))
        (is (re-matches #"https://example\.com/unsubscribe/.+"
                        (second
                         (re-find
                          #"(https://example\.com/unsubscribe/[^<]+)"
                          (get-in effects [1 1 :form-params :html])))))
        (is (= :biff.sqlite.fx/execute
               (get-in effects [3 :_finish 0])))))))
