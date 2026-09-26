(ns com.platypub.work.suppress-bounces-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.work.suppress-bounces :as bounces]
            [tick.core :as tick]))

(def now (tick/instant "2026-09-13T00:00:00Z"))

(deftest suppress-bounces-states
  (let [{:keys [start page finish]} (bounces/suppress-bounces)]
    (let [request (:request (start {:biff.fx/now        now
                                    :mailersend/api-key (delay "secret")}))]
      (is (= :get (:method request)))
      (is (= (tick/long (tick/<< now (tick/of-hours 24)))
             (get-in request [:query-params :date_from]))))
    (is (= {:biff.fx/return nil} (finish {} {:emails #{}})))
    (let [paged  (page
                  {}
                  {:emails     #{}
                   :page-count 0
                   :request    {}

                   :response
                   {:status 200

                    :body
                    {:data [{:email {:recipient
                                     {:email " BOUNCE@example.com "}}}
                            {:email {:recipient
                                     {:email "spam@example.com"}}}
                            {:email nil}]}}})
          result (finish {} paged)]
      (is (= :biff.sqlite.fx/execute (first result)))
      (is (= #{"bounce@example.com" "spam@example.com"}
             (get-in result [1 :where 2]))))))
