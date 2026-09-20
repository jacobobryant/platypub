(ns com.platypub.work.suppress-bounces-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.work.suppress-bounces :as bounces]
            [tick.core :as tick]))

(def now (tick/instant "2026-09-13T00:00:00Z"))

(deftest suppress-bounces-states
  (let [[fetch suppress] (bounces/suppress-bounces)]
    (let [request (second (fetch {:biff.fx/now        now
                                  :mailersend/api-key (delay "secret")}))]
      (is (= :get (:method request)))
      (is (= (str (tick/<< now (tick/of-hours 24)))
             (get-in request [:query-params :date_from]))))
    (is (= {:biff.fx/return nil} (suppress {} {:status 500})))
    (let [result (suppress
                  {}
                  {:status 200

                   :body
                   {:data [{:email " BOUNCE@example.com "}
                           {:recipient {:email "spam@example.com"}}
                           {:email nil}]}})]
      (is (= :biff.sqlite.fx/execute (first result)))
      (is (= #{"bounce@example.com" "spam@example.com"}
             (get-in result [1 :where 2]))))))
