(ns com.platypub.app.auth-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.app.auth :as auth]
            [tick.core :as tick])
  (:import [java.util UUID]))

(deftest get-user-id-states
  (let [[query result] (auth/get-user-id)

        user-id (random-uuid)]
    (is (= [:biff.graph.fx/query
            {:user/email "person@example.com"}
            [:user/id]]
           (query {} "person@example.com")))
    (is (= user-id
           (result {} {:user/id user-id})))))

(deftest create-user-states
  (let [[count-users insert-user result] (auth/create-user)

        user-id
        (UUID/fromString
         (str "01900000-0000-7000-8000-"
              "000000000001"))

        now (tick/instant "2026-01-01T00:00:00Z")

        state {:email "person@example.com", :users {:global/user-count 1}}

        insert-ctx
        {:biff.fx/now               now,
         :biff.fx/random-uuid7-seq  [user-id],
         :platypub/waitlist-enabled false}]
    (is (= {:email "person@example.com",
            :users [:biff.graph.fx/query [:global/user-count]]}
           (count-users {} {:email "person@example.com"})))
    (testing "tier selection"
      (doseq [[users waitlist-enabled expected-tier]
              [[0 false :user.tier/admin]
               [1 true :user.tier/waitlist]
               [1 false :user.tier/free]]]
        (is (= expected-tier
               (get-in (insert-user
                        (assoc insert-ctx
                               :platypub/waitlist-enabled waitlist-enabled)
                        (assoc state :users {:global/user-count users}))
                       [:_write 1 :values 0 :user/tier 1])))))
    (is (= :biff.sqlite.fx/execute
           (get-in (insert-user insert-ctx state) [:_write 0])))
    (is (= user-id (result {} {:user-id user-id})))))
