(ns com.platypub.app.biff-admin-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.app.biff-admin :as admin]
            [com.platypub.lib.email :as email]
            [tick.core :as tick]))

(deftest alert-email-callback-is-available-at-startup
  (let [system ((:biff.core/init admin/module) (atom []))]
    (is (identical? #'email/send-email
                    (:biff.admin/send-email system)))
    (is (instance? clojure.lang.IAtom
                   (:biff.admin/pstats system)))))

(deftest get-users-state-test
  (let [[state] (admin/get-users)

        joined-at (tick/instant "2026-01-01T00:00:00Z")

        user-id (random-uuid)]
    (is (= [{:user-id   user-id
             :email     "person@example.com"
             :joined-at joined-at}]
           (state {}
                  {:global/users [{:user/id        user-id
                                   :user/email     "person@example.com"
                                   :user/joined-at joined-at}]})))))
