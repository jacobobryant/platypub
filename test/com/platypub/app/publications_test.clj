(ns com.platypub.app.publications-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.app.publications :as publications]
            [tick.core :as tick])
  (:import [java.util UUID]))

(deftest publications-page-state-test
  (let [[state] (publications/publications-page)]
    (is (= 200
           (:status
            (state {} {:request/user {:user/active-publications   []
                                      :user/archived-publications []}}))))))

(deftest create-publication-states-test
  (let [[prepare
         fetch-page
         discover
         load-existing
         fetch-feed
         load-canonical
         load-posts
         store-content
         persist
         finish
         create]
        (publications/create-publication)

        user-id (random-uuid)

        publication-id (UUID/fromString "01900000-0000-7000-8000-000000000001")

        now (tick/instant "2026-01-01T00:00:00Z")]
    (let [loaded (fetch-page
                  {}
                  (prepare {} {:request/user {:user/id user-id}

                               :request/new-publication
                               {:publication/url "https://example.com"}}))]
      (is (= :biff.fx/http (get-in loaded [:response 0]))))
    (testing "missing and ambiguous discoveries are actionable"
      (is (= 422
             (get-in (discover {} {:url      "https://example.com"
                                   :data     {:user-id user-id}
                                   :response {:status 200
                                              :body   "<html></html>"}})
                     [:biff.fx/return :status])))
      (is (= 204
             (get-in
              (discover
               {}
               {:url  "https://example.com"
                :data {:user-id user-id}

                :response
                {:status 200
                 :body   (str
                          "<link type='application/rss+xml' href='/one'>"
                          "<link type='application/rss+xml' href='/two'>")}})
              [:biff.fx/return :status]))))
    (let [started
          (discover
           {}
           {:url  "https://example.com"
            :data {:user-id user-id}

            :response
            {:status 200
             :body   (str "<link type='application/rss+xml' "
                          "href='https://feed.example'>")}})

          feed-id (random-uuid)

          old-feed {:feed/id feed-id :feed/url "https://feed.example"}

          existing (load-existing {} started)

          fetched (fetch-feed {} (assoc existing :old-feed old-feed))

          canonical
          (load-canonical
           {:biff.fx/now now}
           (assoc fetched
                  :response
                  {:status  200
                   :uri     "https://feed.example"
                   :headers {"content-type" "application/rss+xml"}
                   :body    (str "<rss><channel><title>News</title><item>"
                                 "<guid>1</guid><description>Body</description>"
                                 "</item></channel></rss>")}))

          posts
          (load-posts
           {:biff.fx/now              now
            :biff.fx/random-uuid7-seq (repeatedly 4 random-uuid)}
           (assoc canonical :canonical old-feed))

          stored
          (store-content
           {:biff.fx/random-uuid7-seq (repeatedly random-uuid)}
           (assoc posts :existing {:feed/posts []}))

          persisted (persist {} stored)

          synced (finish {} persisted)

          result
          (create {:biff.fx/now              now
                   :biff.fx/random-uuid7-seq [publication-id]}
                  synced)]
      (is (= "https://feed.example" (:url started)))
      (is (= :biff.graph.fx/query (get-in existing [:old-feed 0])))
      (is (= :biff.fx/http (get-in fetched [:response 0])))
      (is (= :biff.graph.fx/query (get-in canonical [:canonical 0])))
      (is (= :biff.graph.fx/query (get-in posts [:existing 0])))
      (is (seq (get-in persisted [:sync :write-statements])))
      (is (= (:sync persisted) synced))
      (is (= :biff.sqlite.fx/authorized-write-tx
             (get-in result [:_write 0])))
      (is (< 2 (count (get-in result [:_write 1]))))
      (is (= publication-id
             (some (fn [statement]
                     (get-in statement [:values 0 :publication/id]))
                   (get-in result [:_write 1]))))
      (is (= {:status 204} (:biff.fx/return result))))))

(deftest create-publication-metadata-state-test
  (let [state (last (publications/create-publication))

        publication-id (random-uuid)

        user-id (random-uuid)

        now (tick/instant "2026-01-01T00:00:00Z")

        result
        (state
         {:biff.fx/now              now
          :biff.fx/random-uuid7-seq [publication-id]}
         {:data             {:user-id    user-id
                             :user-email "owner@example.com"}
          :feed             {:feed/id     1
                             :feed/url    "https://example.com/feed"
                             :description "Description"
                             :author      {:name  "Author"
                                           :url   "https://example.com/author"
                                           :image "https://example.com/a.png"}}
          :success          true
          :post-ids         [(random-uuid)]
          :write-statements []})

        row (some :values (get-in result [:_write 1]))]
    (is (= "Description"
           (:publication/description (first row))))
    (is (= "owner@example.com"
           (:publication/reply-to (first row))))
    (is (= "Author"
           (:publication/default-author-name (first row))))))
