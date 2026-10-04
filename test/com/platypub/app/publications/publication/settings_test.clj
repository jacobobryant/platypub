(ns com.platypub.app.publications.publication.settings-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.app.publications.publication.settings :as settings]
            [tick.core :as tick]))

(def publication
  {:publication/id                   (random-uuid)
   :publication/feed-id              (random-uuid)
   :publication/title                "News"
   :publication/reply-to-address     "owner@example.com"
   :publication/address              "123 Main St"
   :publication/padding-color        "#fff"
   :publication/background-color     "#fff"
   :publication/text-color           "#111"
   :publication/primary-color        "#00f"
   :publication/welcome-html         "Welcome"
   :publication/require-confirmation false})

(def request-settings
  {:publication/title                    " Updated news "
   :publication/reply-to                 " replies@example.com "
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
  (let [[render] (settings/settings-page)
        result   {:request/publication
                  (assoc publication
                         :publication/feed {:feed/url "https://feed.example"})}
        stored   {:publication/id  (:publication/id publication)
                  :settings/kind   "form"
                  :settings/open   true
                  :settings/values {:publication/title "Unsaved title"}}
        loading  (render {} (assoc result :request/tab
                                   {:tab/settings-preview stored}))
        ready    (render {} (assoc result :request/tab
                                   {:tab/settings-preview
                                    (assoc stored :settings/html
                                           "<p>Rendered</p>")}))]
    (is (= {:status 404} (render {} {})))
    (is (= 200 (:status (render {} result))))
    (is (re-find #"Preparing preview" (:body loading)))
    (is (nil? (re-find #"srcdoc=" (:body loading))))
    (is (re-find #"srcdoc=" (:body ready)))
    (is (re-find #"&lt;p&gt;Rendered&lt;/p&gt;" (:body ready)))))

(deftest settings-preview-states-test
  (let [[clear _ _ _ finish] (settings/settings-preview)
        tab-id               (random-uuid)
        request-id           (random-uuid)
        ctx                  {:biff.datastar/tab-id     tab-id
                              :biff.fx/random-uuid7-seq [request-id]}
        result               {:request/preview-kind "form"
                              :request/publication  publication

                              :request/publication-settings
                              {:publication/title       "Unsaved title"
                               :publication/description "Unsaved description"}

                              :request/tab
                              {:tab/settings-preview
                               {:publication/id  (:publication/id publication)
                                :settings/kind   "one"
                                :settings/open   false
                                :settings/html   "<p>Old</p>"
                                :settings/values {:publication/title "Old"}}}}]
    (is (= {:biff.fx/return {:status 404}} (clear ctx {})))
    (is (= {:biff.fx/return {:status 404}}
           (clear ctx (assoc result :request/preview-kind "unknown"))))
    (doseq [kind ["form" "one" "multi"]]
      (let [response (clear ctx (assoc result :request/preview-kind kind))
            stored   (get-in response [:_clear 1 :values 0 :tab-state/data
                                       1 :tab/settings-preview])]
        (is (= (:publication/id publication) (:publication/id stored)))
        (is (= kind (:settings/kind stored)))
        (is (true? (:settings/open stored)))
        (is (= request-id (:settings/request-id stored)))
        (is (nil? (:settings/html stored)))
        (is (= "Unsaved title"
               (get-in stored [:settings/values :publication/title])))))
    (let [current   {:tab/settings-preview
                     {:publication/id      (:publication/id publication)
                      :settings/kind       "one"
                      :settings/open       true
                      :settings/request-id request-id

                      :settings/values
                      {:publication/title "Unsaved title"}}}
          completed (finish {} {:tab-id     tab-id
                                :request-id request-id
                                :html       "<p>New</p>"
                                :latest-tab [{:tab-state/data current}]})
          saved     (get-in completed [:_write 1 :values 0 :tab-state/data
                                       1 :tab/settings-preview])]
      (is (= "<p>New</p>" (:settings/html saved)))
      (is (= {:status 204}
             (finish {} {:tab-id     tab-id
                         :request-id request-id
                         :html       "<p>Stale</p>"
                         :latest-tab [{:tab-state/data
                                       (assoc-in current
                                                 [:tab/settings-preview
                                                  :settings/open]
                                                 false)}]}))))))

(deftest close-settings-preview-test
  (let [[close]  (settings/close-settings-preview)
        preview  {:publication/id  (:publication/id publication)
                  :settings/kind   "one"
                  :settings/open   true
                  :settings/values {:publication/title "News"}}
        response (close {:biff.datastar/tab-id (random-uuid)}
                        {:request/publication publication
                         :request/tab         {:tab/settings-preview preview}})]
    (is (false? (get-in response [:_write 1 :values 0 :tab-state/data
                                  1 :tab/settings-preview :settings/open])))))

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
              {:key          (str object-id ".png")
               :value        "/tmp/image.png"
               :content-type "image/png"
               :headers      {"x-amz-acl" "public-read"}}]
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
      (is (= "replies@example.com"
             (get-in saved [:set-values :publication/reply-to])))
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
