(ns com.platypub.app.biff-admin-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.app.biff-admin :as admin]
            [tick.core :as tick]))

(deftest get-users-state-test
  (let [[state]
        (admin/get-users)

        joined-at
        (tick/instant "2026-01-01T00:00:00Z")

        user-id
        (random-uuid)]
    (is (= [{:user-id   user-id
             :email     "person@example.com"
             :joined-at joined-at}]
           (state {}
                  {:global/users [{:user/id        user-id
                                   :user/email     "person@example.com"
                                   :user/joined-at joined-at}]})))))
