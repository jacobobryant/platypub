(ns com.platypub.lib.middleware-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.lib.middleware :as middleware]))

(deftest app-access-states
  (let [[load-user set-tier result]
        (middleware/app-access)

        user-id
        (random-uuid)]
    (is (= [:biff.graph.fx/query
            {:user/id user-id}
            [:user/id :user/tier {:global/first-user [:user/id]}]]
           (load-user {:session {:uid user-id}})))
    (testing "an existing tier is retained"
      (is (= {:user {:user/id user-id :user/tier :user.tier/free}}
             (set-tier {}
                       {:user {:user/id   user-id
                               :user/tier :user.tier/free}}))))
    (testing "the first user becomes an admin"
      (let [state (set-tier {}
                            {:user   {:user/id user-id}
                             :global {:global/first-user
                                      {:user/id user-id}}})]
        (is (= :user.tier/admin (get-in state [:user :user/tier])))
        (is (= :biff.sqlite.fx/execute
               (get-in state [:_write 0])))))
    (testing "later users respect the waitlist setting"
      (is (= :user.tier/waitlist
             (get-in
              (set-tier {:platypub/waitlist-enabled true}
                        {:user   {:user/id user-id}
                         :global {:global/first-user
                                  {:user/id (random-uuid)}}})
              [:user :user/tier]))))
    (testing "later users receive the free tier when the waitlist is disabled"
      (is (= :user.tier/free
             (get-in
              (set-tier {:platypub/waitlist-enabled false}
                        {:user   {:user/id user-id}
                         :global {:global/first-user
                                  {:user/id (random-uuid)}}})
              [:user :user/tier]))))
    (is (= {:user/id user-id}
           (result {} {:user {:user/id user-id}})))))
