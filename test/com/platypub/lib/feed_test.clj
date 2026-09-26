(ns com.platypub.lib.feed-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.lib.feed :as feed]
            [tick.core :as tick]))

(def parse-feed (ns-resolve 'com.platypub.lib.feed 'parse-feed))
(def parse-response (ns-resolve 'com.platypub.lib.feed 'parse-response))
(def prepare-sync (ns-resolve 'com.platypub.lib.feed 'prepare-sync))

(deftest feed-state-functions-test
  (testing "conditional request headers"
    (let [result (feed/fetch
                  {}
                  {:url      "https://example.com/feed"
                   :old-feed {:feed/etag          "etag"
                              :feed/last-modified "yesterday"}})]
      (is (= {"If-None-Match"     "etag"
              "If-Modified-Since" "yesterday"}
             (select-keys (get-in result [:response 1 :headers])
                          ["If-None-Match" "If-Modified-Since"])))))
  (testing "canonical feed lookup"
    (let [result (feed/load-canonical
                  {}
                  {:url      "https://example.com/feed"
                   :response {:status 304}})]
      (is (= :biff.graph.fx/query (get-in result [:canonical 0])))))
  (testing "writes execute immediately for background work"
    (with-redefs-fn {prepare-sync
                     (fn [& _]
                       {:statement {:update :feed}
                        :result    {:success true}})}
      #(let [stored (feed/store-content {} {})]
         (is (= :biff.sqlite.fx/execute
                (get-in (feed/persist {} stored) [:_write 0])))))
    (with-redefs-fn {prepare-sync
                     (fn [& _]
                       {:statements [{:update :feed} {:update :post}]
                        :result     {:success true}})}
      #(let [stored (feed/store-content {} {})]
         (is (= :biff.sqlite.fx/execute-tx
                (get-in (feed/persist {} stored) [:_write 0]))))))
  (testing "browser-initiated writes are deferred for authorization"
    (with-redefs-fn {prepare-sync
                     (fn [& _]
                       {:statements [{:insert-into :feed}]
                        :result     {:success true}})}
      #(let [stored (feed/store-content {} {:data {:defer-write true}})
             result (feed/persist {} stored)]
         (is (nil? (:_write result)))
         (is (= [{:insert-into :feed}]
                (get-in result [:sync :write-statements]))))))
  (testing "load and finish states preserve pipeline data"
    (is (= :biff.graph.fx/query
           (get-in (feed/load-existing {} {:url "feed"}) [:old-feed 0])))
    (is (= [[:? :feed/id]
            [:? :feed/url]
            [:? :feed/created-at]
            [:? :feed/etag]
            [:? :feed/last-modified]]
           (get-in (feed/load-existing {} {:url "feed"}) [:old-feed 2])))
    (is (= {:success true} (feed/finish {} {:sync {:success true}}))))
  (testing "post loading supports new feeds"
    (let [now (tick/instant "2026-09-14T00:00:00Z")

          feed-id (random-uuid)

          post-id (random-uuid)

          content-id (random-uuid)

          result
          (feed/load-posts
           {:biff.fx/now              now
            :biff.fx/random-uuid7-seq [feed-id]}
           {:canonical {:feed/url "feed"}
            :parsed    {:url   "feed"
                        :posts [{:content {:text "Body"}}]}})

          stored
          (feed/store-content
           {:biff.fx/random-uuid7-seq [post-id content-id]}
           (assoc result :existing {:feed/posts []}))]
      (is (= {:feed/id feed-id} (get-in result [:existing 1])))
      (is (= feed-id (:feed-id result)))
      (is (not (contains? result :ids)))
      (is (= :feed (get-in stored [:write-statements 0 :insert-into]))))))

(deftest parse-supported-feeds
  (testing "RSS"
    (let [result (parse-feed
                  (str "<rss><channel><title>News</title><item>"
                       "<guid>1</guid><title>Hello</title>"
                       "<description>&lt;p&gt;Body&lt;/p&gt;</description>"
                       "</item></channel></rss>")
                  "application/rss+xml")]
      (is (= "News" (:title result)))
      (is (= "Hello" (get-in result [:posts 0 :title])))))
  (testing "JSON Feed"
    (is (= {:text "Body"}
           (get-in
            (parse-feed
             (str "{\"version\":\"https://jsonfeed.org/version/1.1\","
                  "\"title\":\"News\",\"items\":[{\"id\":\"1\","
                  "\"content_text\":\"Body\"}]}")
             "application/feed+json")
            [:posts 0 :content])))))

(deftest response-processing
  (let [response {:status  200
                  :uri     "https://example.com/feed"
                  :headers {"content-type" "application/rss+xml"}
                  :body    "<rss><channel><title>News</title></channel></rss>"}]
    (is (= "News" (:title (parse-response
                           "https://example.com/feed" response)))))
  (is (:not-modified
       (parse-response "https://example.com/feed" {:status 304})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Unable to fetch feed"
                        (parse-response "https://example.com/feed"
                                        {:status 500}))))

(deftest prepare-sync-statements
  (let [now (tick/instant "2026-01-01T00:00:00Z")

        feed-id (random-uuid)

        result
        (prepare-sync
         now
         feed-id
         (repeatedly random-uuid)
         {:feed/id feed-id, :feed/url "https://example.com/feed"}
         [{:post/id (random-uuid)}]
         {:not-modified true})]
    (is (= :feed (get-in result [:statement :update])))
    (is (= feed-id (get-in result [:result :feed :feed/id])))
    (is (:success (:result result)))))
