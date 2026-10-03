(ns com.platypub.app.publications.publication.settings-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.app.publications.publication.settings :as settings]
            [com.platypub.test-helpers :as helpers]
            [com.platypub.uicomp.publication :as uicomp.publication]
            [tick.core :as tick]))

(def publication
  {:publication/id                   (random-uuid)
   :publication/feed-id              (random-uuid)
   :publication/title                "News"
   :publication/address              "123 Main St"
   :publication/padding-color        "#fff"
   :publication/background-color     "#fff"
   :publication/text-color           "#111"
   :publication/primary-color        "#00f"
   :publication/welcome-html         "Welcome"
   :publication/require-confirmation false})

(def request-settings
  {:publication/title                    " Updated news "
   :publication/address                  " 123 Main St\nSuite 4 "
   :publication/archive-url              " https://example.com/archive/ "
   :publication/description              " Description "
   :publication/hide-form-title          true
   :publication/form-placeholder         " Enter thine email address "
   :publication/form-style               :publication.form-style/pill
   :publication/email-style              :publication.email-style/letter
   :publication/site-url                 " https://example.com "
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
  (let [[prepare render] (settings/settings-page)
        result           {:request/publication
                          (assoc publication
                                 :publication/feed {:feed/url "https://feed.example"})}
        now              (tick/instant "2026-01-01T00:00:00Z")
        ctx              {:biff.fx/now              now
                          :biff.fx/random-uuid7-seq (repeatedly random-uuid)}]
    (is (= {:status 404} (render {} (prepare ctx {}))))
    (is (= 200 (:status (render {} (prepare ctx result)))))
    (let [stored      {:publication/id    (:publication/id publication)
                       :settings/kind     "form"
                       :settings/revision (random-uuid)

                       :settings/values
                       {:publication/title       "Unsaved title"
                        :publication/description "Unsaved description"}}
          prepared    (prepare ctx (assoc result :request/tab
                                          {:tab/settings-preview stored}))
          render-form (:publication/ui-subscribe-form
                       (helpers/resolve-resolver
                        uicomp.publication/subscribe-form
                        {}
                        (get-in prepared [:preview-form 1])))
          form-data   {:publication/ui-subscribe-form render-form}
          response    (render {} (assoc prepared :preview-form form-data))]
      (is (= [:publication/ui-subscribe-form]
             (get-in prepared [:preview-form 2])))
      (is (= 200 (:status response)))
      (is (re-find #"Sign up for Unsaved title" (:body response)))
      (is (re-find #"Unsaved description" (:body response)))
      (is (re-find #"DOMPurify.sanitize" (:body response)))
      (is (nil? (re-find #"cf-turnstile" (:body response)))))
    (doseq [kind ["one" "multi"]]
      (let [stored   {:publication/id    (:publication/id publication)
                      :settings/kind     kind
                      :settings/revision (random-uuid)
                      :settings/values   {:publication/title "Unsaved title"}}
            prepared (prepare ctx (assoc result :request/tab
                                         {:tab/settings-preview stored}))]
        (is (= (if (= kind "one") 1 2)
               (count (get-in prepared [:preview-email 1 :send/posts]))))
        (let [post (first (get-in prepared [:preview-email 1 :send/posts]))]
          (is (= 3 (count (re-seq #"<p>"
                                 (get-in post [:post/content :content/html])))))
          (is (= 500 (count (:post/excerpt post)))))
        (is (= "Unsaved title"
               (get-in prepared [:preview-email 1 :send/publication
                                 :publication/title])))
        (let [rendered (render {}
                               (assoc prepared :preview-email
                                      {:send/html "<p>Rendered</p>"}))]
          (is (re-find #"&lt;p&gt;Rendered&lt;/p&gt;"
                       (:body rendered))))))))

(deftest settings-preview-states-test
  (let [[write]  (settings/settings-preview)
        tab-id   (random-uuid)
        revision (random-uuid)
        ctx      {:biff.datastar/tab-id     tab-id
                  :biff.fx/random-uuid7-seq [revision]}
        result   {:request/preview-kind "form"
                  :request/publication  publication

                  :request/publication-settings
                  {:publication/title       "Unsaved title"
                   :publication/description "Unsaved description"}

                  :request/tab {}}]
    (is (= {:biff.fx/return {:status 404}} (write ctx {})))
    (is (= {:biff.fx/return {:status 404}}
           (write ctx (assoc result :request/preview-kind "unknown"))))
    (doseq [kind ["form" "one" "multi"]]
      (let [response (write ctx (assoc result :request/preview-kind kind))
            stored   (get-in response [:_write 1 :values 0 :tab-state/data
                                       1 :tab/settings-preview])]
        (is (= {:status 204} (:biff.fx/return response)))
        (is (= (:publication/id publication) (:publication/id stored)))
        (is (= kind (:settings/kind stored)))
        (is (= revision (:settings/revision stored)))
        (is (= "Unsaved title"
               (get-in stored [:settings/values :publication/title])))))))

(deftest upload-image-states-test
  (let [[prepare write] (settings/upload-image)
        publication-id  (:publication/id publication)
        object-id       (random-uuid)]
    (is (= {:biff.fx/return {:status 422}}
           (prepare {} {})))
    (let [tab-id (random-uuid)

          prepared
          (prepare
           {:path-params              {:field "banner"}
            :biff.datastar/signals    {:request/image
                                       {:content-type "image/png"
                                        :tempfile     "/tmp/image.png"}}
            :biff.datastar/tab-id     tab-id
            :biff.fx/random-uuid7-seq [object-id]}
           {:request/publication {:publication/id publication-id}
            :request/tab         {}})

          result
          (write {:platypub/cdn-url-template "https://cdn.example/%s"}
                 prepared)]
      (is (= [:platypub.fx/put-object
              (str object-id ".png")
              (get-in prepared [:_upload 2])
              "image/png"]
             (:_upload prepared)))
      (is (= :biff.sqlite.fx/execute (get-in result [:_write 0])))
      (is (= (str "https://cdn.example/" object-id ".png")
             (get-in result [:_write 1 :values 0 :tab-state/data 1
                             :tab/publication-images
                             :publication/banner-image-url])))
      (is (= 204 (get-in result [:biff.fx/return :status]))))))

(deftest save-settings-states-test
  (let [[save
         start
         load-existing
         fetch
         load-canonical
         load-posts
         store-content
         persist
         finish
         write]
        (settings/save-settings)

        now (tick/instant "2026-01-01T00:00:00Z")

        result
        {:request/publication
         (assoc publication
                :publication/feed {:feed/url "https://feed.example"})

         :request/feed {:feed/url "https://new-feed.example"}

         :request/publication-settings request-settings}]
    (is (= {:biff.fx/return {:status 404}} (save {} {})))
    (let [saved (save {:biff.fx/now now} result)]
      (is (= "Updated news" (get-in saved [:set-values :publication/title])))
      (is (= "123 Main St Suite 4"
             (get-in saved [:set-values :publication/address])))
      (is (= "https://example.com/archive/"
             (get-in saved [:set-values :publication/archive-url])))
      (is (= "Enter thine email address"
             (get-in saved [:set-values :publication/form-placeholder])))
      (is (= :publication.form-style/pill
             (get-in saved [:set-values :publication/form-style])))
      (is (true? (get-in saved [:set-values :publication/hide-form-title])))
      (is (= :publication.email-style/letter
             (get-in saved [:set-values :publication/email-style])))
      (is (= [:lift :publication.email-style/letter]
             (get-in saved [:_write 1 :set :publication/email-style])))
      (is (= [:lift :publication.form-style/pill]
             (get-in saved [:_write 1 :set :publication/form-style])))
      (is (= "https://example.com"
             (get-in saved [:set-values :publication/site-url])))
      (is (= now
             (get-in saved
                     [:set-values :publication/automatic-send-threshold])))
      (is (= :biff.sqlite.fx/authorized-write
             (get-in saved [:_write 0])))
      (let [started (start {} saved)

            old-feed
            {:feed/id  (:publication/feed-id publication)
             :feed/url "https://new-feed.example"}

            existing (load-existing {} started)

            fetched (fetch {} (assoc existing :old-feed old-feed))

            canonical
            (load-canonical
             {:biff.fx/now now}
             (assoc fetched
                    :response
                    {:status  200
                     :uri     "https://new-feed.example"
                     :headers {"content-type" "application/rss+xml"}
                     :body    (str "<rss><channel><item><guid>1</guid>"
                                   "<description>Body</description>"
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

            synced (finish {} persisted)]
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
          (is (= (:publication/feed-id publication)
                 (some #(get-in % [:set :publication/feed-id])
                       (get-in written [:_write 1]))))
          (is (= {:status 204} (:biff.fx/return written)))))
      (let [automatic-publication
            (assoc publication :publication/automatic-send-threshold now)

            written
            (write
             {:biff.fx/now now}
             {:data             {:publication automatic-publication}
              :feed             {:feed/id (:publication/feed-id publication)}
              :post-ids         [(random-uuid)]
              :success          true
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
