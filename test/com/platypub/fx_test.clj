(ns com.platypub.fx-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.fx :as fx]))

(deftest low-level-handlers
  (let [state (atom {:emails []})]
    (is (= {:emails []} (fx/deref-atom {} state)))
    (is (= {:emails ["message"]}
           (fx/swap-atom {:platypub/state state}
                         :platypub/state
                         `update
                         :emails
                         `conj
                         "message")))
    (is (= {:emails []} (fx/reset-atom {} state {:emails []}))))
  (is (= "hello" (fx/read-uploaded-file {} (.getBytes "hello")))))

(deftest module-only-exposes-low-level-effects
  (is (every? (set (keys (:biff.fx/handlers fx/module)))
              [:biff.fx/sleep
               :platypub.fx/swap!
               :platypub.fx/print])))
