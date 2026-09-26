(ns com.platypub.model.subscriber-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.model.subscriber :as subscriber]
            [com.platypub.test-helpers :as helpers]
            [tick.core :as tick]))

(deftest lookup-resolvers-test
  (is (= {:subscriber/id 1}
         (helpers/resolve-sql
          subscriber/by-publication-email
          {:subscriber/publication-id 2
           :subscriber/email          "reader@example.com"}
          [{:subscriber/id 1}])))
  (is (= {:subscriber/id 1}
         (helpers/resolve-sql
          subscriber/by-confirmation-token
          {:subscriber/confirmation-token (byte-array [1])}
          [{:subscriber/id 1}]))))

(deftest active-resolver-test
  (is (= {:subscriber/active true}
         (helpers/resolve-resolver
          subscriber/active
          {}
          {:subscriber/id                   1
           :subscriber/suppressed           false
           :subscriber/unsubscribed-at      nil
           :subscriber/require-confirmation false})))
  (is (= {:subscriber/active false}
         (helpers/resolve-resolver
          subscriber/active
          {}
          {:subscriber/id 1 :subscriber/suppressed true}))))

(deftest confirmation-token-active-resolver-test
  (let [now (tick/instant "2026-09-21T00:00:00Z")]
    (is (= {:subscriber/confirmation-token-active true}
           (helpers/resolve-resolver
            subscriber/confirmation-token-active
            {:biff.fx/now now}
            {:subscriber/confirmation-triggered-at
             (tick/<< now (tick/of-hours 23))})))
    (is (= {:subscriber/confirmation-token-active false}
           (helpers/resolve-resolver
            subscriber/confirmation-token-active
            {:biff.fx/now now}
            {:subscriber/confirmation-triggered-at
             (tick/<< now (tick/of-hours 25))})))))
