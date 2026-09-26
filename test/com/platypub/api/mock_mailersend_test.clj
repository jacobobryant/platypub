(ns com.platypub.api.mock-mailersend-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.api.mock-mailersend :as mailersend]))

(deftest reset-state-test
  (let [[state] (mailersend/reset-state)

        store (atom nil)]
    (is (= [:platypub/reset-atom
            store
            {:emails [] :activities []}]
           (state {:platypub/mock-mailersend-state store})))))

(deftest send-email-state-test
  (let [[state] (mailersend/send-email)

        store (atom {:emails [] :activities []})]
    (is (= {:biff.fx/return {:status 404}}
           (state {:platypub/mock-mailersend-enabled false})))
    (let [result (state {:platypub/mock-mailersend-enabled true
                         :platypub/mock-mailersend-state   store
                         :biff.fx/random-uuid7-seq         [(random-uuid)]

                         :params {:to [{:email "reader@example.com"}]}})]
      (is (= [:platypub.fx/swap!
              :platypub/mock-mailersend-state
              `update
              :emails
              `conj]
             (subvec (get result :_store) 0 5)))
      (is (= 202 (get-in result [:biff.fx/return :status]))))))

(deftest activities-states-test
  (let [[load-state response] (mailersend/activities)

        store (atom {:activities [{:email "a@example.com"}]})]
    (is (= {:biff.fx/return {:status 404}}
           (load-state {:platypub/mock-mailersend-enabled false})))
    (is (= {:state [:platypub/deref store]}
           (load-state {:platypub/mock-mailersend-enabled true
                        :platypub/mock-mailersend-state   store})))
    (is (= {:status 200
            :body   {:data  (:activities @store)
                     :links {:next nil}
                     :meta  {:current_page 1}}}
           (response {} {:state @store})))))
