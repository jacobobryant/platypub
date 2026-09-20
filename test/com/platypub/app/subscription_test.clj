(ns com.platypub.app.subscription-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.app.subscription :as subscription]
            [tick.core :as tick]))

(deftest subscribe-page-state-test
  (let [[state] (subscription/subscribe-page)]
    (is (= {:status 404} (state {} {})))
    (is (= 200
           (:status
            (state {}
                   {:publication/id               (random-uuid)
                    :publication/title            "News"
                    :publication/padding-color    "#fff"
                    :publication/background-color "#fff"
                    :publication/text-color       "#111"
                    :publication/primary-color    "#00f"}))))))

(deftest submit-subscription-states-test
  (let [[load-existing persist respond] (subscription/submit-subscription)

        now (tick/instant "2026-09-13T00:00:00Z")

        uuids (repeatedly 2 random-uuid)

        publication
        {:publication/id                   (random-uuid)
         :publication/title                "News"
         :publication/require-confirmation true
         :publication/welcome-html         "<p>Welcome</p>"}]
    (testing "missing publication and invalid email stop before effects"
      (is (= {:biff.fx/return {:status 404}}
             (load-existing {} {:request/subscription
                                {:subscriber/email "person@example.com"}})))
      (is (contains?
           (load-existing
            {}
            (assoc publication
                   :request/subscription {:subscriber/email "not-an-email"}))
           :biff.fx/return)))
    (let [loaded
          (load-existing
           {}
           (assoc publication
                  :request/subscription
                  {:subscriber/email        " PERSON@example.com "
                   :subscriber/headers      {:user-agent "test"}
                   :subscriber/form-params  {:email " PERSON@example.com "}
                   :subscriber/query-params {:source "test"}}))]
      (is (= [:biff.graph.fx/query
              {:subscriber/publication-id (:publication/id publication)
               :subscriber/email          "person@example.com"}]
             (subvec (:existing loaded) 0 2)))
      (testing "new and resubscribed subscribers are written"
        (doseq [existing [nil
                          {:subscriber/id              (second uuids)
                           :subscriber/unsubscribed-at now
                           :subscriber/suppressed      false}]]
          (let [result
                (persist {:biff.fx/now              now
                          :biff.fx/random-uuid7-seq uuids}
                         (assoc loaded
                                :existing existing))]
            (is (= :biff.sqlite.fx/execute
                   (get-in result [:_write 0])))
            (is (= :biff.graph.fx/query
                   (get-in result [:active 0]))))))
      (testing "an active or suppressed existing subscriber is not written"
        (doseq [existing [{:subscriber/id         (second uuids)
                           :subscriber/suppressed true}
                          {:subscriber/id         (second uuids)
                           :subscriber/suppressed false}]]
          (is (nil?
               (:_write
                (persist {:biff.fx/now              now
                          :biff.fx/random-uuid7-seq uuids}
                         (assoc loaded
                                :existing existing)))))))
      (testing "confirmation, welcome, and no-email branches"
        (let [confirmation
              (respond {:biff.fx/now              now
                        :biff.fx/random-uuid7-seq uuids
                        :platypub/base-url        "https://platypub.test"}
                       {:publication publication
                        :email       "person@example.com"

                        :subscriber
                        {:subscriber/id                   (first uuids)
                         :subscriber/require-confirmation true}

                        :previously-active false
                        :active            {:subscriber/active false}})

              welcome
              (respond {:biff.fx/now              now
                        :biff.fx/random-uuid7-seq uuids}
                       {:publication publication
                        :email       "person@example.com"

                        :subscriber
                        {:subscriber/id                   (first uuids)
                         :subscriber/confirmed-at         now
                         :subscriber/require-confirmation true}

                        :previously-active false
                        :active            {:subscriber/active true}})

              no-email
              (respond {:biff.fx/now              now
                        :biff.fx/random-uuid7-seq uuids}
                       {:publication publication
                        :email       "person@example.com"

                        :subscriber
                        {:subscriber/id         (first uuids)
                         :subscriber/suppressed true}

                        :previously-active true
                        :active            {:subscriber/active false}})]
          (is (= 2 (count (:biff.fx/seq confirmation))))
          (is (= :biff.sqlite.fx/execute
                 (get-in confirmation [:biff.fx/seq 0 :_write 0])))
          (is (= {:status 204} (:biff.fx/return confirmation)))
          (is (= 1 (count (:biff.fx/seq welcome))))
          (is (nil? (:biff.fx/seq no-email))))))))
