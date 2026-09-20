(ns com.platypub.app.publications-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.app.publications :as publications]
            [tick.core :as tick])
  (:import [java.util UUID]))

(deftest publications-page-state-test
  (let [[state] (publications/publications-page)]
    (is (= 200 (:status (state {} {:request/user {:user/publications []}}))))))

(deftest create-publication-states-test
  (let [[prepare
         fetch-page
         discover
         load-existing
         fetch-feed
         load-canonical
         load-posts
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
    (testing "zero or multiple discovered feeds return without writing"
      (doseq [body ["<html></html>"
                    (str "<link type='application/rss+xml' href='/one'>"
                         "<link type='application/rss+xml' href='/two'>")]]
        (is (= {:biff.fx/return {:status 204}}
               (discover {} {:url      "https://example.com"
                             :data     {:user-id user-id}
                             :response {:status 200 :body body}})))))
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
           {}
           (assoc fetched :response {:status 304
                                     :uri    "https://feed.example"}))

          posts
          (load-posts
           {:biff.fx/now              now
            :biff.fx/random-uuid7-seq [(random-uuid)]}
           (assoc canonical :canonical old-feed))

          persisted (persist {} (assoc posts :existing {:feed/posts []}))

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
         {:data             {:user-id user-id}
          :feed             {:feed/id     1
                             :feed/url    "https://example.com/feed"
                             :description "Description"
                             :author      {:name  "Author"
                                           :url   "https://example.com/author"
                                           :image "https://example.com/a.png"}}
          :post-ids         []
          :write-statements []})

        row (some :values (get-in result [:_write 1]))]
    (is (= "Description"
           (:publication/description (first row))))
    (is (= "Author"
           (:publication/default-author-name (first row))))))
