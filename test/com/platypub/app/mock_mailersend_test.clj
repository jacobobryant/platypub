(ns com.platypub.app.mock-mailersend-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.app.mock-mailersend :as mock]))

(deftest inbox-states-test
  (let [[load-state render-state]
        (mock/inbox)

        store
        (atom {:emails []})]
    (is (= {:biff.fx/return {:status 404}}
           (load-state {:platypub/mock-mailersend-enabled false})))
    (is (= {:state [:platypub/deref store]}
           (load-state {:platypub/mock-mailersend-enabled true,
                        :platypub/mock-mailersend-state   store})))
    (is (= 200 (:status (render-state {} {:state @store}))))))
