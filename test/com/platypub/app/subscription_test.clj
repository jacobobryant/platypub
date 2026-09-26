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
                   {:request/subscription-publication
                    {:publication/id               (random-uuid)
                     :publication/title            "News"
                     :publication/padding-color    "#fff"
                     :publication/background-color "#fff"
                     :publication/text-color       "#111"
                     :publication/primary-color    "#00f"}}))))))

(deftest embeddable-middleware-test
  (let [handler (subscription/wrap-embeddable
                 (constantly {:status  200
                              :headers {"X-Frame-Options" "SAMEORIGIN"}}))]
    (is (= {"Content-Security-Policy" "frame-ancestors *"}
           (:headers (handler {:uri "/subscribe/id"}))))
    (is (= {"X-Frame-Options" "SAMEORIGIN"}
           (:headers (handler {:uri "/other"}))))))

(deftest submit-subscription-states-test
  (let [[prepare load-existing persist respond]
        (subscription/submit-subscription)

        now (tick/instant "2026-09-13T00:00:00Z")

        uuids (repeatedly 2 random-uuid)

        publication
        {:publication/id                   (random-uuid)
         :publication/title                "News"
         :publication/require-confirmation true
         :publication/welcome-html         "<p>Welcome</p>"}]
    (testing "missing publication and invalid email stop before effects"
      (is (= {:biff.fx/return {:status 404}}
             (prepare {:biff.auth/skip-captcha true}
                      {:request/subscription-publication nil

                       :request/subscription
                       {:subscriber/email "person@example.com"}})))
      (is (contains?
           (prepare
            {:biff.auth/skip-captcha true}
            {:request/subscription-publication publication

             :request/subscription
             {:subscriber/email "not-an-email"}})
           :biff.fx/return)))
    (testing "captcha verification and rejection"
      (let [verification
            (prepare
             {:biff.auth/skip-captcha       false
              :biff.auth/turnstile-secret   (delay "secret")
              :platypub/hcaptcha-secret-key (delay "fallback")}
             {:request/subscription-publication publication

              :request/subscription
              {:subscriber/email        "person@example.com"
               :request/turnstile-token "token"}})]
        (is (= :biff.fx/http (get-in verification [:_captcha 0])))
        (is (= "secret"
               (get-in verification [:_captcha 1 :form-params :secret])))
        (is (= 200
               (get-in (load-existing
                        {}
                        (assoc verification
                               :_captcha {:body {:success false}}))
                       [:biff.fx/return :status])))))
    (let [loaded
          (prepare
           {:biff.auth/skip-captcha true
            :headers                {:user-agent "test"}
            :form-params            {:email " PERSON@example.com "}
            :query-params           {:source "test"}}
           {:request/subscription-publication publication

            :request/subscription
            {:subscriber/email " PERSON@example.com "}})

          loaded (load-existing {} loaded)]
      (is (= [:biff.graph.fx/query
              {:subscriber/publication-id (:publication/id publication)
               :subscriber/email          "person@example.com"}]
             (subvec (:existing loaded) 0 2)))
      (is (= {:user-agent "test"}
             (get-in loaded [:request-data :subscriber/headers])))
      (testing "new and resubscribed subscribers are written"
        (doseq [existing [nil
                          {:subscriber/id              (second uuids)
                           :subscriber/unsubscribed-at now
                           :subscriber/suppressed      false}]]
          (let [result
                (persist {:biff.fx/now              now
                          :biff.fx/random-uuid7-seq uuids}
                         (assoc loaded
                                :existing existing))

                effects (:biff.fx/seq result)]
            (is (= :biff.sqlite.fx/execute
                   (get-in effects [0 :_write 0])))
            (when-not existing
              (is (= "{\"user-agent\":\"test\"}"
                     (String.
                      (get-in effects
                              [0 :_write 1 :values 0
                               :subscriber/headers])))))
            (is (= [:biff.graph.fx/query
                    (:subscriber result)
                    [:subscriber/active]]
                   (:active (second effects)))))))
      (testing "an active or suppressed existing subscriber is not written"
        (doseq [existing [{:subscriber/id         (second uuids)
                           :subscriber/suppressed true}
                          {:subscriber/id         (second uuids)
                           :subscriber/suppressed false}]]
          (let [effects
                (:biff.fx/seq
                 (persist {:biff.fx/now              now
                           :biff.fx/random-uuid7-seq uuids}
                          (assoc loaded :existing existing)))]
            (is (= 2 (count effects)))
            (is (nil? (get-in effects [0 :_write])))
            (is (= :biff.graph.fx/query
                   (get-in effects [1 :active 0]))))))
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
          (is (= 200 (get-in confirmation [:biff.fx/return :status])))
          (is (= 1 (count (:biff.fx/seq welcome))))
          (is (nil? (:biff.fx/seq no-email))))))))
