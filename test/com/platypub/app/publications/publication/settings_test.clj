(ns com.platypub.app.publications.publication.settings-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.app.publications.publication.settings :as settings]
            [tick.core :as tick]))

(def publication
  {:publication/id                   (random-uuid)
   :publication/feed-id              (random-uuid)
   :publication/title                "News"
   :publication/padding-color        "#fff"
   :publication/background-color     "#fff"
   :publication/text-color           "#111"
   :publication/primary-color        "#00f"
   :publication/welcome-html         "Welcome"
   :publication/require-confirmation false})

(def request-settings
  {:publication/title                    " Updated news "
   :publication/description              " Description "
   :publication/intro                    ""
   :publication/banner-image-url         ""
   :publication/default-author-name      ""
   :publication/default-author-url       ""
   :publication/default-author-image-url ""
   :publication/padding-color            "#fff"
   :publication/background-color         "#fff"
   :publication/text-color               "#111"
   :publication/primary-color            "#00f"
   :publication/filter-tag               ""
   :publication/remove-tag               ""
   :publication/welcome-html             "Updated"
   :publication/automatic-sending        true
   :publication/require-confirmation     true})

(deftest settings-page-state-test
  (let [[state] (settings/settings-page)]
    (is (= {:status 404} (state {} {})))
    (is (= 200
           (:status
            (state {}
                   {:request/publication
                    (assoc publication
                           :publication/feed
                           {:feed/url "https://feed.example"})}))))))

(deftest save-settings-states-test
  (let [[save
         start
         load-existing
         fetch
         load-canonical
         load-posts
         persist
         finish
         write]
        (settings/save-settings)

        now
        (tick/instant "2026-01-01T00:00:00Z")

        result
        {:request/publication
         (assoc publication
                :publication/feed
                {:feed/url "https://feed.example"})

         :request/feed {:feed/url "https://new-feed.example"}

         :request/publication-settings request-settings}]
    (is (= {:biff.fx/return {:status 404}} (save {} {})))
    (let [saved (save {:biff.fx/now now} result)]
      (is (= "Updated news" (get-in saved [:set-values :publication/title])))
      (is (= now
             (get-in saved
                     [:set-values :publication/automatic-send-threshold])))
      (is (= :biff.sqlite.fx/authorized-write
             (get-in saved [:_write 0])))
      (let [started
            (start {} saved)

            old-feed
            {:feed/id  (:publication/feed-id publication)
             :feed/url "https://new-feed.example"}

            existing
            (load-existing {} started)

            fetched
            (fetch {} (assoc existing :old-feed old-feed))

            canonical
            (load-canonical
             {}
             (assoc fetched
                    :response {:status 304
                               :uri    "https://new-feed.example"}))

            posts
            (load-posts
             {:biff.fx/now              now
              :biff.fx/random-uuid7-seq [(random-uuid)]}
             (assoc canonical :canonical old-feed))

            persisted
            (persist {} (assoc posts :existing {:feed/posts []}))

            synced
            (finish {} persisted)]
        (is (= "https://new-feed.example" (:url started)))
        (is (= :biff.graph.fx/query (get-in existing [:old-feed 0])))
        (is (= :biff.fx/http (get-in fetched [:response 0])))
        (is (= :biff.graph.fx/query (get-in canonical [:canonical 0])))
        (is (= :biff.graph.fx/query (get-in posts [:existing 0])))
        (is (seq (get-in persisted [:sync :write-statements])))
        (is (= (:sync persisted) synced))
        (let [written (write {:biff.fx/now now} synced)]
          (is (= :biff.sqlite.fx/authorized-write-tx
                 (get-in written [:_write 0])))
          (is (< 2 (count (get-in written [:_write 1]))))
          (is (= {:status 204} (:biff.fx/return written)))))
      (let [automatic-publication
            (assoc publication :publication/automatic-send-threshold now)

            written
            (write
             {:biff.fx/now now}
             {:data             {:publication automatic-publication}
              :feed/id          (:publication/feed-id publication)
              :post-ids         []
              :write-statements []})]
        (is (= now
               (get-in written
                       [:_write
                        1
                        0
                        :set
                        :publication/automatic-send-threshold]))))
      (is (= {:biff.fx/return {:status 204}}
             (start {} (assoc saved :feed-changed false))))
      (is (nil?
           (get-in
            (save {:biff.fx/now now}
                  (assoc-in
                   result
                   [:request/publication-settings
                    :publication/automatic-sending]
                   false))
            [:set-values :publication/automatic-send-threshold]))))))
