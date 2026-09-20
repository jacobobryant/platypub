(ns com.platypub.model.send-attempt-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.model.send-attempt :as send-attempt]
            [com.platypub.test-helpers :as helpers]))

(deftest by-send-resolver-test
  (is (= {:send/attempts [{:send-attempt/id 2}]}
         (helpers/resolve-sql
          send-attempt/for-send
          {:send/id 1}
          (fn [statement]
            (is (= [:send-attempt/id] (:select statement)))
            [{:send-attempt/id 2}])))))
