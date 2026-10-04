(ns com.platypub.app.subscription-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.app.subscription :as subscription]
            [com.platypub.test-helpers :as helpers]
            [com.platypub.uicomp.publication :as uicomp.publication]
            [tick.core :as tick]))

(deftest subscribe-page-state-test
  (let [[state]     (subscription/subscribe-page)
        publication {:publication/id               (random-uuid)
                     :publication/title            "News"
                     :publication/padding-color    "#fff"
                     :publication/background-color "#fff"
                     :publication/text-color       "#111"
                     :publication/primary-color    "#00f"}
        render      (get (helpers/resolve-resolver
                          uicomp.publication/subscribe-form
                          {:biff.auth/turnstile-site-key "site"}
                          publication)
                         :publication/ui-subscribe-form)
        with-form   (assoc publication :publication/ui-subscribe-form render)
        response    (state {}
                           {:request/subscription-publication with-form})]
    (is (= {:status 404} (state {} {})))
    (is (= 200 (:status response)))
    (is (re-find #"<title>News</title>" (:body response)))
    (is (re-find #"min-h-screen" (:body response)))
    (is (nil? (re-find #"platypub:resize" (:body response))))
    (is (nil? (re-find #"biff-datastar-sse" (:body response))))
    (is (some? (re-find #"cf-turnstile" (:body response))))
    (let [embedded (state {:query-params {"embed" "1"}}
                          {:request/subscription-publication with-form})]
      (is (re-find #"<title>News</title>" (:body embedded)))
      (is (re-find #"platypub:resize" (:body embedded)))
      (is (nil? (re-find #"min-h-screen" (:body embedded)))))))

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
         :publication/from-name            "News"
         :publication/reply-to-address     "replies@example.com"
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
        (is (= :biff.fx/http (get-in verification [:captcha-response 0])))
        (is (= :always (get-in verification [:captcha-response 1 :coerce])))
        (is (= "secret"
               (get-in verification
                       [:captcha-response 1 :form-params :secret])))
        (is (= 200
               (get-in (load-existing
                        {}
                        (assoc verification
                               :captcha-response {:body {:success false}}))
                       [:biff.fx/return :status]))))
      (let [fallback
            (prepare
             {:biff.auth/skip-captcha       false
              :biff.auth/turnstile-secret   (delay "secret")
              :platypub/hcaptcha-secret-key (delay "fallback")}
             {:request/subscription-publication publication

              :request/subscription
              {:subscriber/email        "person@example.com"
               :request/turnstile-token ""
               :request/hcaptcha-token  "fallback-token"}})]
        (is (= :hcaptcha (:captcha-provider fallback)))
        (is (= "fallback-token"
               (get-in fallback
                       [:captcha-response 1 :form-params :response])))
        (is (= "fallback"
               (get-in fallback
                       [:captcha-response 1 :form-params :secret]))))
      (let [missing
            (prepare
             {:biff.auth/skip-captcha false}
             {:request/subscription-publication publication

              :request/subscription
              {:subscriber/email        "person@example.com"
               :request/turnstile-token ""}})]
        (is (nil? (:captcha-response missing)))
        (is (contains? (load-existing {} missing) :biff.fx/return))))
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
                        :mailersend/api-key       (delay "secret")
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
                        :biff.fx/random-uuid7-seq uuids
                        :mailersend/api-key       (delay "secret")}
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
          (is (= "Confirm your subscription"
                 (get-in confirmation
                         [:biff.fx/seq 1 :_email 1 :form-params :subject])))
          (is (= "News"
                 (get-in confirmation
                         [:biff.fx/seq 1 :_email 1 :form-params :from :name])))
          (is (= "replies@example.com"
                 (get-in confirmation
                         [:biff.fx/seq 1 :_email 1 :form-params
                          :reply_to :email])))
          (is (= 1 (count (:biff.fx/seq welcome))))
          (is (= "Welcome"
                 (get-in welcome
                         [:biff.fx/seq 0 :_email 1 :form-params :subject])))
          (is (= "replies@example.com"
                 (get-in welcome
                         [:biff.fx/seq 0 :_email 1 :form-params
                          :reply_to :email])))
          (is (nil? (:biff.fx/seq no-email))))))))

(deftest verified-captcha-reaches-subscriber-write-test
  (let [publication-id (random-uuid)
        writes         (atom [])
        publication    {:publication/id                   publication-id
                        :publication/title                "News"
                        :publication/require-confirmation false
                        :publication/welcome-html         "<p>Welcome</p>"}

        result
        (subscription/submit-subscription
         {:biff.auth/skip-captcha     false
          :biff.auth/turnstile-secret (delay "secret")

          :biff.fx/handlers
          {:biff.graph.fx/query
           (fn [_ctx & args]
             (if (= 1 (count args))
               {:request/subscription-publication publication

                :request/subscription
                {:subscriber/email        "person@example.com"
                 :request/turnstile-token "token"}}
               (when (contains? (first args) :subscriber/id)
                 {:subscriber/active true})))

           :biff.fx/http
           (fn [_ctx _request]
             {:status 200 :body {:success true}})

           :biff.sqlite.fx/execute
           (fn [_ctx statement]
             (swap! writes conj statement))}})]
    (is (= 200 (:status result)))
    (is (= 1 (count @writes)))
    (is (= "person@example.com"
           (get-in (first @writes) [:values 0 :subscriber/email])))))
