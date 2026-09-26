(ns com.platypub.model.request-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.model.request :as request]
            [com.platypub.lib.tokens :as tokens]))

(defn- resolve-resolver
  ([resolver ctx]
   (resolve-resolver resolver ctx {}))
  ([resolver ctx input]
   ((:biff.graph/resolve-fn resolver)
    (assoc ctx :biff.graph/input input))))

(defn- resolve-with-effects
  [resolver ctx input execute]
  ((:biff.graph/resolve-fn resolver)
   (assoc ctx
          :biff.graph/input input
          :biff.fx/handlers
          {:biff.sqlite.fx/execute execute

           :platypub/read-uploaded-file (fn [_ value] (str "read:" value))})))

(deftest request-parameter-resolvers-test
  (let [user-id        (random-uuid)
        publication-id (random-uuid)]
    (is (= {:request/user {:user/id user-id}}
           (resolve-resolver request/user {:session {:uid (str user-id)}})))
    (is (= {:request/subscription-publication
            {:publication/id publication-id}}
           (resolve-resolver
            request/subscription-publication
            {:path-params {:publication-id (str publication-id)}}))))
  (is (= {:request/new-publication {:publication/url "https://example.com/feed"}}
         (resolve-resolver
          request/publication-url
          {:biff.datastar/signals
           {:request/publication-url "https://example.com/feed"}})))
  (is (= {:request/pagination {:page/number 3 :page/limit 50 :page/offset 100}}
         (resolve-resolver request/pagination
                           {:query-params {"page" "3"}})))
  (is (= {:request/pagination {:page/number 1 :page/limit 50 :page/offset 0}}
         (resolve-resolver request/pagination
                           {:query-params {"page" "invalid"}}))))

(deftest publication-resolver-test
  (let [user-id        (random-uuid)
        publication-id (random-uuid)]
    (is (= {:request/publication {:publication/id publication-id}}
           (resolve-with-effects
            request/publication
            {:session     {:uid (str user-id)}
             :path-params {:publication-id (str publication-id)}}
            {}
            (fn [_ statement]
              (is (= [:and
                      [:= :publication/id publication-id]
                      [:= :publication/user-id user-id]]
                     (:where statement)))
              [{:publication/id publication-id}]))))
    (is (nil? (resolve-with-effects
               request/publication
               {:session     {:uid (str user-id)}
                :path-params {:publication-id "invalid"}}
               {}
               (fn [_ _] (throw (Exception. "must not query"))))))
    (is (nil? (resolve-with-effects
               request/publication
               {:session     {:uid (str user-id)}
                :path-params {:publication-id (str publication-id)}}
               {}
               (fn [_ _] []))))))

(deftest tab-state-resolver-test
  (is (= {:request/tab {:tab/background-color :white}}
         (resolve-with-effects
          request/tab-state
          {}
          {}
          (fn [_ _] (throw (Exception. "must not query"))))))
  (let [tab-id (random-uuid)]
    (is (= {:request/tab
            {:tab/background-color         :white
             :tab/admin-publication-search "search"}}
           (resolve-with-effects
            request/tab-state
            {:biff.datastar/tab-id tab-id}
            {}
            (fn [_ statement]
              (is (= {:select [:tab-state/data]
                      :from   :tab-state
                      :where  [:= :tab-state/id tab-id]}
                     statement))
              [{:tab-state/data {:tab/admin-publication-search "search"}}])))))
  (let [tab-id (random-uuid)]
    (is (= {:request/tab {:tab/background-color :white}}
           (resolve-with-effects
            request/tab-state
            {:form-params {"biff_datastar_client-tab-id" (str tab-id)}}
            {}
            (fn [_ statement]
              (is (= [:= :tab-state/id tab-id] (:where statement)))
              []))))))

(deftest search-and-collection-resolvers-test
  (is (= {:request/admin-publication-search {:publication/search "current"}}
         (resolve-resolver
          request/admin-publication-search
          {:biff.datastar/signals {:publication/search "current"}}
          {:request/tab {:tab/admin-publication-search "saved"}})))
  (is (= {:request/admin-publications []}
         (resolve-resolver
          request/admin-publications
          {}
          {:request/user                     {:user/tier :user.tier/admin}
           :request/admin-publication-search {:publication/search ""}})))
  (is (= {:request/admin-publications [{:publication/id 1}]}
         (resolve-with-effects
          request/admin-publications
          {}
          {:request/user                     {:user/tier :user.tier/admin}
           :request/admin-publication-search {:publication/search "news"}}
          (fn [_ statement]
            (is (= :or (first (:where statement))))
            [{:publication/id 1}]))))
  (is (= {:request/admin-publications []}
         (resolve-resolver
          request/admin-publications
          {}
          {:request/user                     {:user/tier :user.tier/free}
           :request/admin-publication-search {:publication/search "news"}})))
  (is (= {:request/subscriber-search {:subscriber/search "saved"}}
         (resolve-resolver
          request/subscriber-search
          {}
          {:request/tab
           {:tab/subscriber-search {:subscriber/search "saved"}}})))
  (is (= {:request/subscribers [{:subscriber/id 1}]}
         (resolve-with-effects
          request/subscribers
          {}
          {:request/publication       {:publication/id 2}
           :request/subscriber-search {:subscriber/search "reader"}
           :request/pagination        {:page/offset 0 :page/limit 50}}
          (fn [_ statement]
            (is (= :and (first (:where statement))))
            [{:subscriber/id 1}]))))
  (let [subscriber-id (random-uuid)
        user-id       (random-uuid)]
    (is (= {:request/subscriber {:subscriber/id subscriber-id}}
           (resolve-with-effects
            request/subscriber
            {:path-params {:subscriber-id (str subscriber-id)}}
            {:request/user {:user/id user-id}}
            (fn [_ statement]
              (is (= [:= :publication/user-id user-id]
                     (get-in statement [:where 2])))
              [{:subscriber/id subscriber-id}]))))
    (is (nil? (resolve-with-effects
               request/subscriber
               {:path-params {:subscriber-id (str subscriber-id)}}
               {:request/user {:user/id user-id}}
               (fn [_ _] []))))
    (is (nil? (resolve-with-effects
               request/subscriber
               {}
               {:request/user {:user/id user-id}}
               (fn [_ _] (throw (Exception. "must not query"))))))))

(deftest public-request-resolvers-test
  (let [token-bytes (byte-array [1 2 3])

        token
        (.encodeToString
         (.withoutPadding (java.util.Base64/getUrlEncoder))
         token-bytes)

        subscriber-id (random-uuid)

        claims
        {:subscriber-id subscriber-id
         :email         "reader@example.com"
         :exp           4102444800}]
    (is (= {:request/subscription
            {:subscriber/email "reader@example.com"}}
           (resolve-resolver
            request/subscription
            {:headers      {"user-agent" "test"}
             :query-params {"source" "embed"}

             :biff.datastar/signals
             {:subscription/email "reader@example.com"}})))
    (is (java.util.Arrays/equals
         token-bytes
         (get-in (resolve-resolver request/confirmation-token
                                   {:path-params {:token token}})
                 [:request/confirmation :request/token])))
    (is (nil? (resolve-resolver request/confirmation-token
                                {:path-params {:token "%%%"}})))
    (with-redefs [tokens/process (fn [_ _ _] claims)]
      (is (= {:request/unsubscribe-claims
              {:subscriber/id      subscriber-id
               :subscriber/email   "reader@example.com"
               :request/expiration 4102444800
               :request/token      "signed"}}
             (resolve-resolver
              request/unsubscribe-claims
              {:path-params                 {:token "signed"}
               :platypub/unsubscribe-secret (delay "secret")}))))
    (with-redefs [tokens/process
                  (constantly {:subscriber-id subscriber-id})]
      (is (nil? (resolve-resolver
                 request/unsubscribe-claims
                 {:path-params                 {:token "signed"}
                  :platypub/unsubscribe-secret (delay "secret")}))))
    (is (= {:request/feed {:feed/url "https://example.com/feed"}

            :request/publication-settings
            {:publication/title                "Title"
             :publication/automatic-sending    true
             :publication/require-confirmation false}}
           (resolve-resolver
            request/publication-settings-request
            {:biff.datastar/signals
             {:request/feed-url                 "https://example.com/feed"
              :publication/title                "Title"
              :publication/automatic-sending    "true"
              :publication/require-confirmation false}})))))

(deftest owned-request-resolvers-test
  (let [user-id (random-uuid)

        publication-id (random-uuid)

        post-id (random-uuid)]
    (let [tier-key (keyword "request" (str "tier-" user-id))]
      (is (= {:request/admin-user-tier
              {:user/id      user-id
               :user/tier    :user.tier/waitlist
               :request/tier "free"}}
             (resolve-with-effects
              request/admin-user-tier
              {:path-params           {:user-id (str user-id)}
               :biff.datastar/signals {tier-key "free"}}
              {:request/user {:user/tier :user.tier/admin}}
              (fn [_ _] [{:user/id   user-id
                          :user/tier :user.tier/waitlist}])))))
    (is (nil? (resolve-with-effects
               request/admin-user-tier
               {:path-params {:user-id (str user-id)}}
               {:request/user {:user/tier :user.tier/free}}
               (fn [_ _] (throw (Exception. "must not query"))))))
    (is (= {:request/admin-publication-import
            {:publication/id publication-id
             :request/csv    "email\nreader@example.com"}}
           (resolve-with-effects
            request/admin-publication-import
            {:path-params {:publication-id (str publication-id)}

             :biff.datastar/signals {:request/csv "email\nreader@example.com"}}
            {:request/user {:user/tier :user.tier/admin}}
            (fn [_ _] [{:publication/id publication-id}]))))
    (is (= {:request/admin-publication-import
            {:publication/id publication-id
             :request/csv    "email\nreader@example.com"}}
           (resolve-with-effects
            request/admin-publication-import
            {:path-params {:publication-id (str publication-id)}

             :form-params
             {"request_csv"
              [{:name     "subscribers.csv"
                :mime     "text/csv"
                :contents "ZW1haWwKcmVhZGVyQGV4YW1wbGUuY29t"}]}}
            {:request/user {:user/tier :user.tier/admin}}
            (fn [_ _] [{:publication/id publication-id}]))))
    (is (nil? (resolve-with-effects
               request/admin-publication-import
               {:path-params {:publication-id (str publication-id)}

                :biff.datastar/signals
                {:request/csv "email\nreader@example.com"}}
               {:request/user {:user/tier :user.tier/free}}
               (fn [_ _] (throw (Exception. "must not query"))))))
    (is (= {:request/send-selection {:send/post-ids [post-id]}}
           (resolve-with-effects
            request/send-selection
            {}
            {:request/publication {:publication/id publication-id}

             :request/tab
             {:tab/send-preview {:send/post-ids [post-id]}}}
            (fn [_ statement]
              (is (= publication-id (get-in statement [:where 2 2])))
              [{:post/id post-id}]))))
    (is (= {:request/send-selection {:send/post-ids []}}
           (resolve-with-effects
            request/send-selection
            {:biff.datastar/signals {:send/post-ids [post-id]}}
            {:request/publication {:publication/id publication-id}
             :request/tab         {}}
            (fn [_ _] []))))
    (is (= {:request/send-selection {:send/post-ids []}}
           (resolve-resolver
            request/send-selection
            {:biff.datastar/signals {:send/post-ids ["invalid"]}}
            {:request/publication {:publication/id publication-id}
             :request/tab         {}})))
    (is (= {:request/send-posts [{:post/id post-id}]}
           (resolve-resolver
            request/send-posts
            {}
            {:request/send-selection {:send/post-ids [post-id]}})))
    (is (= {:request/send-posts []}
           (resolve-resolver
            request/send-posts
            {}
            {:request/send-selection {:send/post-ids []}})))
    (is (= {:request/send-preview
            {:send/subject   "Subject"
             :send/html      "<p>Preview</p>"
             :send/text      "Preview"
             :send/from-name "Publication"
             :send/reply-to  "owner@example.com"
             :send/posts     [{:post/id post-id}]}}
           (resolve-resolver
            request/send-preview
            {}
            {:request/publication {:publication/id publication-id}

             :request/tab
             {:tab/send-preview
              {:publication/id publication-id
               :send/subject   "Subject"
               :send/html      "<p>Preview</p>"
               :send/text      "Preview"
               :send/from-name "Publication"
               :send/reply-to  "owner@example.com"
               :send/post-ids  [post-id]}}})))
    (is (nil?
         (resolve-resolver
          request/send-preview
          {}
          {:request/publication {:publication/id publication-id}

           :request/tab
           {:tab/send-preview {:publication/id (random-uuid)}}})))))
