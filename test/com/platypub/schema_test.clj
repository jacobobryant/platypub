(ns com.platypub.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.biffweb.sqlite :as sqlite]
            [com.platypub.schema :as schema]
            [tick.core :as tick]))

(deftest column-helper-test
  (is (= {:type :text}
         (schema/text)))
  (is (= {:type        :uuid
          :ref         :publication/id
          :required    true
          :index       true
          :unique-with [:subscriber/email]}
         (schema/ref* :publication/id :required :index
                      :unique-with [:subscriber/email])))
  (is (= {:type :edn :extra-schema :any}
         (schema/edn* :any))))

(deftest owned-write-authorization-test
  (let [user-id (random-uuid)

        publication-id (random-uuid)

        subscriber-id (random-uuid)

        now (tick/instant "2026-01-01T00:00:00Z")

        ctx
        {:session                 {:uid user-id}
         :biff.sqlite/before-conn :before
         :biff.sqlite/after-conn  :after}]
    (testing "publication updates query ownership"
      (with-redefs [sqlite/execute
                    (fn [_ statement]
                      (when (= :publication (:from statement))
                        [{:publication/id publication-id}]))]
        (is (schema/authorize
             ctx
             [{:table  :publication
               :op     :update
               :before {:publication/id      publication-id
                        :publication/user-id user-id
                        :publication/title   "Old"}
               :after  {:publication/id      publication-id
                        :publication/user-id user-id
                        :publication/title   "New"}}])))
      (with-redefs [sqlite/execute (fn [_ _] [])]
        (is (not (schema/authorize
                  ctx
                  [{:table  :publication
                    :op     :update
                    :before {:publication/id      publication-id
                             :publication/user-id user-id}
                    :after  {:publication/id      publication-id
                             :publication/user-id user-id
                             :publication/title   "Other"}}])))))
    (testing "subscriber and post updates query ownership"
      (with-redefs [sqlite/execute (fn [_ _] [{:owned true}])]
        (is (schema/authorize
             ctx
             [{:table  :subscriber
               :op     :update
               :before {:subscriber/id subscriber-id}
               :after  {:subscriber/id              subscriber-id
                        :subscriber/unsubscribed-at now}}]))
        (is (schema/authorize
             ctx
             [{:table  :post
               :op     :update
               :before {:post/id publication-id :post/present-as-of nil}
               :after  {:post/id publication-id :post/present-as-of now}}]))))))

(deftest authorization-rejects-request-specific-evidence-test
  (let [publication-id (random-uuid)

        subscriber-id (random-uuid)

        subscriber
        {:subscriber/id             subscriber-id
         :subscriber/email          "reader@example.com"
         :subscriber/publication-id publication-id}]
    (with-redefs [sqlite/execute (fn [_ _] [{:publication/id publication-id}])]
      (is (not
           (schema/authorize
            {:path-params             {:id (str publication-id)}
             :biff.datastar/signals   {:subscription/email
                                       (:subscriber/email subscriber)}
             :biff.sqlite/before-conn :before}
            [{:table :subscriber
              :op    :create
              :after (assoc subscriber
                            :subscriber/subscribed-at
                            (tick/instant "2026-01-01T00:00:00Z")
                            :subscriber/require-confirmation true)}]))))
    (is (not
         (schema/authorize
          {:path-params {:token "request-token"}}
          [{:table  :subscriber
            :op     :update
            :before subscriber
            :after  (assoc subscriber
                           :subscriber/unsubscribed-at
                           (tick/instant "2026-01-01T00:00:00Z"))}])))))

(deftest authorization-transaction-ownership-test
  (let [user-id (random-uuid)

        publication-id (random-uuid)

        send-id (random-uuid)

        content-id (random-uuid)

        send-post-id (random-uuid)

        ctx
        {:session                 {:uid user-id}
         :biff.sqlite/before-conn :before
         :biff.sqlite/after-conn  :after}]
    (with-redefs [sqlite/execute (fn [_ _] [{:owned true}])]
      (is (schema/authorize
           ctx
           [{:table  :content
             :op     :create
             :before nil
             :after  {:content/id content-id}}
            {:table  :send
             :op     :create
             :before nil
             :after  {:send/id             send-id
                      :send/publication-id publication-id
                      :send/content-id     content-id}}
            {:table  :send-post
             :op     :create
             :before nil
             :after  {:send-post/id      send-post-id
                      :send-post/send-id send-id}}])))))

(deftest authorization-does-not-trust-preloaded-admin-test
  (let [user-id (random-uuid)]
    (with-redefs [sqlite/execute (fn [_ _] [])]
      (is (not
           (schema/authorize
            {:session {:uid user-id}

             :platypub/user {:user/id user-id :user/tier :user.tier/admin}

             :biff.sqlite/before-conn :before}
            [{:table  :subscriber
              :op     :delete
              :before {:subscriber/id (random-uuid)}}]))))))

(deftest feed-write-authorization-test
  (let [user-id (random-uuid)]
    (with-redefs [sqlite/execute (fn [_ _] [{:owned true}])]
      (is (schema/authorize
           {:session                {:uid user-id}
            :biff.sqlite/after-conn :after}
           [{:table :feed
             :op    :create
             :after {:feed/id (random-uuid)}}
            {:table :post
             :op    :create
             :after {:post/id (random-uuid)}}
            {:table :content
             :op    :update
             :after {:content/id (random-uuid)}}])))))
