(ns com.platypub.lib.email-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.lib.email :as email]))

(deftest send-email-states
  (let [[deliver-message result] (email/send-email)

        message {:to "reader@example.com"}]
    (is (true? (deliver-message {} message)))
    (is (= :biff.fx/http
           (first (deliver-message
                   {:mailersend/api-key (delay "secret")}
                   message))))
    (is (true? (result {} true)))
    (is (true? (result {} {:status 202})))
    (is (false? (result {} {:status 500})))))
