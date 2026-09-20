(ns com.platypub.lib.subscriber-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.lib.subscriber :as subscriber]))

(deftest email-normalization-and-activity
  (is (= "person@example.com"
         (subscriber/normalize-email "  Person@Example.COM ")))
  (is (subscriber/valid-email? "person@example.com"))
  (is (not (subscriber/valid-email? "invalid"))))
