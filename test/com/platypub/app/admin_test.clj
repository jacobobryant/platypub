(ns com.platypub.app.admin-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.app.admin :as control]
            [tick.core :as tick]))

(deftest dashboard-state-test
  (let [[state] (control/dashboard)]
    (is (= 200
           (:status
            (state {}
                   {:request/admin-publication-search
                    {:publication/search ""}

                    :global/users               []
                    :request/admin-publications []}))))))

(deftest set-tier-state-test
  (let [[state]
        (control/set-tier)

        user-id
        (random-uuid)]
    (testing "valid transition"
      (let [result (state {} {:request/admin-user-tier
                              {:user/id      user-id
                               :user/tier    :user.tier/waitlist
                               :request/tier "free"}})]
        (is (= :biff.sqlite.fx/authorized-write
               (get-in result [:_write 0])))
        (is (= {:status 204} (:biff.fx/return result)))))
    (testing "invalid transition"
      (is (= {:status 204}
             (state {} {:request/admin-user-tier
                        {:user/id      user-id
                         :user/tier    :user.tier/free
                         :request/tier "waitlist"}}))))))

(deftest update-search-state-test
  (let [[state]
        (control/update-search)

        tab-id
        (random-uuid)]
    (is (= :biff.sqlite.fx/execute
           (get-in (state {:biff.datastar/tab-id tab-id}
                          {:request/admin-publication-search
                           {:publication/search "example"}

                           :request/tab {}})
                   [:_search 0])))
    (is (= {:status 204}
           (:biff.fx/return
            (state {:biff.datastar/tab-id tab-id}
                   {:request/admin-publication-search
                    {:publication/search "example"}

                    :request/tab {}}))))))

(deftest import-subscribers-states
  (let [[load-existing write-rows]
        (control/import-subscribers)

        publication-id
        (random-uuid)

        subscriber-id
        (random-uuid)

        now
        (tick/instant "2026-01-01T00:00:00Z")]
    (is (= {:publication-id publication-id,
            :csv            "email\nperson@example.com",
            :existing       [:biff.graph.fx/query
                             {:publication/id publication-id}
                             [{:publication/subscribers
                               [:subscriber/email]}]]}
           (load-existing
            {}
            {:request/admin-publication-import
             {:publication/id publication-id
              :request/csv    "email\nperson@example.com"}})))
    (is (= {:_write
            [:biff.sqlite.fx/authorized-write
             {:insert-into :subscriber,
              :values      [{:subscriber/id                   subscriber-id,
                             :subscriber/email                "new@example.com",
                             :subscriber/publication-id       publication-id,
                             :subscriber/subscribed-at        now,
                             :subscriber/require-confirmation false}]}]

            :biff.fx/return {:status 204}}
           (write-rows {:biff.fx/now              now,
                        :biff.fx/random-uuid7-seq [subscriber-id]}
                       {:publication-id publication-id,
                        :csv            (str "email\nold@example.com\n"
                                             "new@example.com"),

                        :existing
                        {:publication/subscribers
                         [{:subscriber/email "old@example.com"}]}})))
    (is (= {:_write nil, :biff.fx/return {:status 204}}
           (write-rows {:biff.fx/now              now,
                        :biff.fx/random-uuid7-seq [subscriber-id]}
                       {:publication-id publication-id,
                        :csv            "email\nold@example.com",

                        :existing
                        {:publication/subscribers
                         [{:subscriber/email "old@example.com"}]}})))))
