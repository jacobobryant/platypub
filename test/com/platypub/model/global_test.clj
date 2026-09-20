(ns com.platypub.model.global-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.model.global :as global]
            [com.platypub.test-helpers :as helpers]))

(deftest database-resolvers-test
  (is (= {:global/users [{:user/id 1}]}
         (helpers/resolve-sql global/users {} [{:user/id 1}])))
  (is (= {:global/active-feeds [{:feed/id 1}]}
         (helpers/resolve-sql global/active-feeds {} [{:feed/id 1}])))
  (is (= {:global/publications [{:publication/id 1}
                                {:publication/id 2}]}
         (helpers/resolve-sql global/publications
                              {}
                              [{:publication/id 1} {:publication/id 2}])))
  (is (= {:global/user-count 2}
         (helpers/resolve-sql global/user-count {} [{:user/count 2}])))
  (is (= {:global/first-user {:user/id 1}}
         (helpers/resolve-sql global/first-user {} [{:user/id 1}])))
  (is (= {:global/stale-sends [{:send/id 1}]}
         (helpers/resolve-sql
          global/stale-sends
          {}
          (fn [statement]
            (is (= [:< :send/progress-at]
                   (subvec (get-in statement [:where 2]) 0 2)))
            (is (inst? (get-in statement [:where 2 2])))
            [{:send/id 1}])))))
