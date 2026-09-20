(ns com.platypub.work.feed-sync-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.work.feed-sync :as feed-sync]
            [tick.core :as tick]))

(deftest feed-sync-consumer-states
  (let [[load-feed
         start
         load-existing
         fetch
         load-canonical
         load-posts
         persist
         finish
         load-publications
         submit]
        (feed-sync/feed-sync-consumer)

        feed
        {:feed/id 1 :feed/url "https://feed"}]
    (is (= [:biff.graph.fx/query
            {:feed/id 1}
            [:feed/id :feed/url]]
           (load-feed {:biff.background/job {:feed-id 1}})))
    (is (= {:biff.fx/return nil} (start {} {})))
    (let [started
          (start {} feed)

          existing
          (load-existing {} started)

          fetched
          (fetch {} (assoc existing :old-feed feed))

          canonical
          (load-canonical
           {}
           (assoc fetched :response {:status 304
                                     :uri    "https://feed"}))

          posts
          (load-posts
           {:biff.fx/now
            (tick/instant "2026-01-01T00:00:00Z")

            :biff.fx/random-uuid7-seq
            [(random-uuid)]}
           (assoc canonical :canonical feed))

          persisted
          (persist {} (assoc posts :existing {:feed/posts []}))]
      (is (= "https://feed" (:url started)))
      (is (= :biff.graph.fx/query (get-in existing [:old-feed 0])))
      (is (= :biff.fx/http (get-in fetched [:response 0])))
      (is (= :biff.graph.fx/query (get-in canonical [:canonical 0])))
      (is (= :biff.graph.fx/query (get-in posts [:existing 0])))
      (is (contains? persisted :_write))
      (is (:success (finish {} persisted))))
    (testing "failed syncs do not enqueue publication checks"
      (is (= {:biff.fx/return nil}
             (load-publications {} {:success false}))))
    (testing "successful syncs enqueue each publication"
      (let [loaded (load-publications
                    {}
                    {:success true :data {:feed feed}})]
        (is (= :biff.graph.fx/query (first (:publications loaded))))
        (is (= [:biff.background.fx/submit-jobs
                :platypub/send-readiness
                [{:publication-id 2}]]
               (submit {} {:publications
                           {:feed/publications [{:publication/id 2}]}})))
        (is (= [:biff.background.fx/submit-jobs
                :platypub/send-readiness
                []]
               (submit {} {:publications {:feed/publications []}})))))))
