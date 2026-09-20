(ns com.platypub.model.user-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.model.user :as user]
            [com.platypub.test-helpers :as helpers]))

(deftest database-resolvers-test
  (is (= {:user/id 1}
         (helpers/resolve-sql user/by-email
                              {:user/email "person@example.com"}
                              [{:user/id 1}])))
  (is (= {:user/publications [{:publication/id 2}]}
         (helpers/resolve-sql user/publications
                              {:user/id 1}
                              [{:publication/id 2}]))))
