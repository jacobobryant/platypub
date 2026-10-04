(ns com.platypub.lib.feed
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [com.platypub.lib.text :as text]
            [dev.onionpancakes.chassis.core :as chassis]
            [remus]
            [tick.core :as tick])
  (:import [java.io ByteArrayInputStream]
           [java.nio.charset StandardCharsets]
           [java.security MessageDigest]))

(def user-agent "platypub")
(defn- sha-256 [value]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes (str value) StandardCharsets/UTF_8))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) digest))))

(defn- author
  [value]
  (let [value (or (first (:authors value)) (:author value))]
    (if (map? value)
      {:name (:name value), :url (or (:uri value) (:url value))}
      {:name value})))

(defn- remus-content
  [entry]
  (let [{:keys [type value]}
        (or (first (:contents entry)) (:description entry))]
    (when (not-empty value)
      (if (or (= "text" type) (= "text/plain" type))
        {:text value}
        {:html value}))))

(defn- parse-xml
  [body]
  (let [feed (remus/parse
              (ByteArrayInputStream.
               (.getBytes body StandardCharsets/UTF_8)))]
    {:title       (:title feed)
     :description (:description feed)
     :author      (author feed)
     :posts       (->> (:entries feed)
                       (keep
                        (fn [entry]
                          (let [url     (:link entry)
                                content (or (remus-content entry)
                                            (when url
                                              {:html (chassis/html
                                                      [:a {:href url} url])}))
                                author  (author entry)]
                            (when content
                              {:guid         (:uri entry)
                               :url          url
                               :title        (:title entry)
                               :content      content
                               :published-at (some-> (or (:published-date entry)
                                                         (:updated-date entry))
                                                     tick/instant)
                               :tags         (mapv :name (:categories entry))
                               :author-name  (:name author)
                               :author-url   (:url author)
                               :author-image (:image author)}))))
                       vec)}))

(defn- json-author
  [value]
  (let [author (or (first (:authors value))
                   (:author value))]
    {:name  (or (:name author) (:author_name value)),
     :url   (or (:url author) (:author_url value)),
     :image (or (:avatar author)
                (:avatar_url author)
                (:author_avatar value))}))

(defn- parse-json-feed
  [body]
  (let [feed (json/parse-string body true)]
    {:title       (:title feed),
     :description (or (:description feed) (:subtitle feed)),
     :author      (json-author feed),

     :posts
     (->>
      (:items feed)
      (map
       (fn [item]
         (let [url (or (:url item) (:external_url item))

               content
               (or (when-some [html (:content_html item)] {:html html})
                   (when-some [text (:content_text item)] {:text text})
                   (when url
                     {:html (chassis/html [:a {:href url} url])}))

               author (json-author item)]
           {:guid         (:id item),
            :url          url,
            :title        (:title item),
            :content      content,
            :published-at (some-> (or (:date_published item)
                                      (:date_modified item))
                                  tick/instant),
            :tags         (vec (:tags item)),
            :author-name  (:name author),
            :author-url   (:url author),
            :author-image (:image author)})))
      (filter :content)
      vec)}))

(defn- parse-feed
  [body content-type]
  (if (or (str/includes? (str/lower-case (or content-type
                                             ""))
                         "json")
          (str/starts-with? (str/triml body) "{"))
    (parse-json-feed body)
    (parse-xml body)))

(defn- request
  [url headers]
  {:method           :get
   :url              url
   :headers          (merge {"User-Agent" user-agent} headers)
   :http-client      {:connect-timeout 10000, :redirect-policy :normal}
   :request-timeout  20000
   :throw-exceptions false
   :as               :string})

(defn- parse-response
  [url response]
  (let [final-url (str (or (:uri response) url))]
    (cond (= 304 (:status response)) {:not-modified true
                                      :url          final-url
                                      :response     response}

          (<= 200 (:status response) 299)
          (assoc (parse-feed (:body response)
                             (get-in response [:headers "content-type"]))
                 :url final-url
                 :response response)

          :else (throw (ex-info "Unable to fetch feed."
                                {:status (:status response)
                                 :url    final-url})))))

(defn- post-match
  [posts {:keys [guid url content-hash]}]
  (some (fn [post]
          (when (or (and guid (= guid (:post/guid post)))
                    (and (nil? guid)
                         url
                         (= url (:post/url post)))
                    (and (nil? guid)
                         (nil? url)
                         (= content-hash
                            (:post/content-hash post))))
            post))
        posts))

(defn- prepare-post
  [post]
  (let [content (:content post)
        plain   (or (:text content) (text/html->text (:html content)))]
    (assoc post
           :content-hash (sha-256 (json/generate-string content))
           :length (count plain)
           :excerpt (subs plain 0 (min 500 (count plain))))))

(defn- post-statements
  [now feed-id {:keys [post post-id content-id new-post]}]
  (let [optional
        (into {}
              (map (fn [[source target]] [target (get post source)]))
              {:guid         :post/guid
               :published-at :post/published-at
               :title        :post/title
               :url          :post/url
               :author-name  :post/author-name
               :author-url   :post/author-url
               :author-image :post/author-image-url})

        fields
        (merge {:post/content-id   content-id
                :post/content-hash (:content-hash post)
                :post/length       (:length post)
                :post/excerpt      (:excerpt post)
                :post/tags         [:lift (not-empty (:tags post))]}
               optional)]
    [(if new-post
       {:insert-into :post
        :values      [(merge fields
                             {:post/id            post-id
                              :post/feed-id       feed-id
                              :post/fetched-at    now
                              :post/present-as-of now})]}
       {:update :post
        :set    fields
        :where  [:= :post/id post-id]})]))

(defn- prepare-sync
  [now feed-id ids feed existing parsed]
  (let [existing (or existing [])]
    (if (:not-modified parsed)
      {:statement {:update :feed
                   :set    {:feed/fetched-at now :feed/failed-syncs 0}
                   :where  [:= :feed/id feed-id]}
       :result    {:feed         (assoc feed :feed/fetched-at now)
                   :post-ids     []
                   :not-modified true
                   :success      true}}
      (let [prepared (mapv prepare-post (:posts parsed))

            id-pairs (partition 2 ids)

            rows
            (mapv (fn [post [post-uuid content-uuid]]
                    (let [match (post-match existing post)]
                      {:post       post
                       :post-id    (or (:post/id match) post-uuid)
                       :content-id (or (:post/content-id match)
                                       content-uuid)
                       :new-post   (nil? match)}))
                  prepared
                  id-pairs)

            headers (:headers (:response parsed))

            feed-row
            {:feed/id            feed-id
             :feed/created-at    (or (:feed/created-at feed) now)
             :feed/fetched-at    now
             :feed/url           (:url parsed)
             :feed/failed-syncs  0
             :feed/etag          (get headers "etag")
             :feed/last-modified (get headers "last-modified")}

            base
            (if (:feed/id feed)
              {:update :feed
               :set    (dissoc feed-row :feed/id :feed/created-at)
               :where  [:= :feed/id feed-id]}
              {:insert-into :feed :values [feed-row]})

            statements
            (into [base] (mapcat #(post-statements now feed-id %))
                  rows)]
        {:statements statements
         :result     {:feed     (merge feed-row
                                       (select-keys parsed
                                                    [:title
                                                     :description
                                                     :author]))
                      :post-ids (mapv :post-id rows)
                      :objects  (mapv (fn [{:keys [content-id post]}]
                                        {:content-id content-id
                                         :data       (:content post)})
                                      rows)
                      :success  true}}))))

(defn load-existing
  [_ctx {:keys [url] :as input}]
  (assoc input :old-feed
         [:biff.graph.fx/query
          {:feed/url url}
          [[:? :feed/id]
           [:? :feed/url]
           [:? :feed/created-at]
           [:? :feed/etag]
           [:? :feed/last-modified]]]))

(defn fetch
  [_ctx {:keys [url old-feed data]}]
  (let [headers
        (if (:force-fetch data)
          {}
          (cond-> {}
            (:feed/etag old-feed)
            (assoc "If-None-Match" (:feed/etag old-feed))
            (:feed/last-modified old-feed)
            (assoc "If-Modified-Since" (:feed/last-modified old-feed))))]
    {:url      url
     :data     data
     :old-feed old-feed
     :response [:biff.fx/http (request url headers)]}))

(defn load-canonical
  [{:biff.fx/keys [now]} {:keys [url old-feed response data]}]
  (try
    (let [parsed (parse-response url response)]
      {:old-feed old-feed
       :data     data
       :parsed   parsed

       :canonical
       [:biff.graph.fx/query
        {:feed/url (:url parsed)}
        [[:? :feed/id]
         [:? :feed/url]
         [:? :feed/created-at]
         [:? :feed/etag]
         [:? :feed/last-modified]]]})
    (catch Exception exception
      (cond-> {:biff.fx/return
               (if (:defer-write data)
                 {:status 422
                  :body   "The feed could not be fetched or parsed."}
                 {:success false
                  :error   (.getMessage exception)
                  :data    data})}
        (:feed/id old-feed)
        (assoc :_failure
               [:biff.sqlite.fx/execute
                {:update :feed
                 :set    {:feed/fetched-at   now
                          :feed/failed-syncs [:+ :feed/failed-syncs 1]}
                 :where  [:= :feed/id (:feed/id old-feed)]}])))))

(defn load-posts
  [{:biff.fx/keys [now random-uuid7-seq]}
   {:keys [old-feed parsed canonical data]}]
  (let [feed (or (when (:feed/id canonical) canonical) old-feed)

        feed-id (or (:feed/id feed) (first random-uuid7-seq))]
    {:now     now
     :data    data
     :feed-id feed-id
     :feed    feed
     :parsed  parsed

     :existing
     [:biff.graph.fx/query
      {:feed/id feed-id}
      [{:feed/posts
        [:post/id
         :post/content-id
         :post/guid
         :post/url
         :post/content-hash]}]]}))

(defn store-content
  [{:biff.fx/keys [random-uuid7-seq]}
   {:keys [now feed-id feed parsed existing data]}]
  (let [{:keys [statement statements result]}
        (prepare-sync now
                      feed-id
                      random-uuid7-seq
                      feed
                      (:feed/posts existing)
                      parsed)

        write-statements (if statement [statement] statements)

        sync-result (assoc result :data data)]
    {:sync-result      sync-result
     :write-statements write-statements

     :biff.fx/seq
     (mapv (fn [{:keys [content-id data]}]
             {:_content [:platypub.fx/put-object
                         {:key          content-id
                          :value        (json/generate-string data)
                          :content-type "application/json"}]})
           (:objects result))}))

(defn persist
  [_ctx {:keys [sync-result write-statements]}]
  (let [statement        (first write-statements)
        single-statement (= 1 (count write-statements))
        data             (:data sync-result)]
    (if (:defer-write data)
      {:sync (assoc sync-result :write-statements write-statements)}
      {:sync   sync-result
       :_write (if single-statement
                 [:biff.sqlite.fx/execute statement]
                 [:biff.sqlite.fx/execute-tx write-statements])})))

(defn finish
  [_ctx state]
  (:sync state))

(def sync-fns
  [load-existing
   fetch
   load-canonical
   load-posts
   store-content
   persist
   finish])
