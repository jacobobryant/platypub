(ns com.platypub.lib.feed
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [com.platypub.lib.text :as text]
            [dev.onionpancakes.chassis.core :as chassis]
            [tick.core :as tick])
  (:import [java.io ByteArrayInputStream]
           [java.nio.charset StandardCharsets]
           [java.security MessageDigest]
           [javax.xml.parsers DocumentBuilderFactory]
           [org.w3c.dom Element Node]))

(def user-agent "platypub")
(def rfc-1123 (tick/formatter "EEE, dd MMM uuuu HH:mm:ss zzz"))

(defn- sha-256 [value]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes (str value) StandardCharsets/UTF_8))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) digest))))

(defn- parse-date
  [value]
  (when (not-empty (str/trim (or value "")))
    (some (fn [f] (try (f value) (catch Exception _ nil)))
          [tick/instant
           #(tick/instant
             (tick/parse-offset-date-time
              %
              (tick/formatter :iso-offset-date-time)))
           #(tick/instant (tick/parse-zoned-date-time % rfc-1123))])))

(defn- children
  [^Node node]
  (let [nodes (.getChildNodes node)]
    (map #(.item nodes %) (range (.getLength nodes)))))

(defn- elements
  [node tag-name]
  (filter #(and (= Node/ELEMENT_NODE (.getNodeType ^Node %))
                (= tag-name
                   (str/lower-case
                    (or (.getLocalName ^Node %)
                        (.getNodeName ^Node %)))))
          (children node)))

(defn- descendant-elements
  [node tag-name]
  (filter #(and (= Node/ELEMENT_NODE (.getNodeType ^Node %))
                (= tag-name
                   (str/lower-case
                    (or (.getLocalName ^Node %)
                        (.getNodeName ^Node %)))))
          (tree-seq #(seq (children %)) children node)))

(defn- text-of
  [node names]
  (some (fn [tag-name]
          (some-> (first (elements node tag-name))
                  .getTextContent
                  str/trim
                  not-empty))
        names))

(defn- attr
  [^Node node attribute-name]
  (when (instance? Element node)
    (some-> ^Element node
            (.getAttribute attribute-name)
            str/trim
            not-empty)))

(defn- link-of
  [entry]
  (or (some (fn [node]
              (when (not= "enclosure" (attr node "rel"))
                (attr node "href")))
            (elements entry "link"))
      (text-of entry ["link"])))

(defn- xml-author
  [node]
  (when-let [author (first (elements node "author"))]
    {:name  (or (text-of author ["name"])
                (some-> author
                        .getTextContent
                        str/trim
                        not-empty)),
     :url   (text-of author ["uri" "url"]),
     :image (text-of author ["avatar" "image"])}))

(defn- xml-item
  [item]
  (let [url (link-of item)

        content (text-of item ["encoded" "content" "description" "summary"])

        author
        (or (xml-author item)
            {:name (text-of item
                            ["creator" "author"])})]
    {:guid         (text-of item ["guid" "id"]),
     :url          url,
     :title        (text-of item ["title"]),
     :content      (or content
                       (when url
                         (chassis/html [:a {:href url} url]))),
     :published-at (parse-date (text-of item
                                        ["published"
                                         "updated"
                                         "pubdate"
                                         "date"])),
     :tags         (->> (concat (elements item "category")
                                (elements item "tag"))
                        (map #(or (attr % "term")
                                  (some-> %
                                          .getTextContent
                                          str/trim)))
                        (remove str/blank?)
                        vec),
     :author-name  (:name author),
     :author-url   (:url author),
     :author-image (:image author)}))

(defn- parse-xml
  [body]
  (let
   [factory
    (doto (DocumentBuilderFactory/newInstance)
      (.setNamespaceAware true)
      (.setFeature
       "http://apache.org/xml/features/disallow-doctype-decl"
       true)
      (.setFeature
       "http://xml.org/sax/features/external-general-entities"
       false)
      (.setFeature
       "http://xml.org/sax/features/external-parameter-entities"
       false))

    doc
    (.parse (.newDocumentBuilder factory)
            (ByteArrayInputStream.
             (.getBytes body StandardCharsets/UTF_8)))

    root (.getDocumentElement doc)

    channel (or (first (elements root "channel")) root)

    items
    (concat (descendant-elements channel "item")
            (descendant-elements channel "entry"))

    author (xml-author channel)]
    {:title       (text-of channel ["title"]),
     :description (text-of channel
                           ["description" "subtitle"]),
     :author      author,
     :posts       (->> items
                       (map xml-item)
                       (filter :content)
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
               (or (:content_html item)
                   (:content_text item)
                   (when url
                     (chassis/html [:a {:href url} url])))

               author (json-author item)]
           {:guid         (:id item),
            :url          url,
            :title        (:title item),
            :content      content,
            :published-at (parse-date
                           (or (:date_published item)
                               (:date_modified item))),
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
   :connect-timeout  10000
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
  (let [text (text/html->text (:content post))]
    (assoc post
           :content-hash (sha-256 (:content post))
           :length (count text)
           :excerpt (subs text 0 (min 500 (count text))))))

(defn- post-statements
  [now feed-id {:keys [post post-id content-id new-post]}]
  (let [optional
        (into {}
              (keep (fn [[source target]]
                      (when-some [value (get post source)]
                        [target value])))
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
                :post/tags         [:lift (:tags post)]}
               optional)]
    [{:insert-into   :content
      :values        [{:content/id content-id

                       :content/data
                       [:lift {:html (:content post)
                               :text (text/html->text (:content post))}]}]
      :on-conflict   [:content/id]
      :do-update-set [:content/data]}
     (if new-post
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
  [now ids feed existing parsed]
  (let [feed-id (or (:feed/id feed) (first ids))

        ids (if (:feed/id feed) ids (rest ids))

        existing (or existing [])]
    (if (:not-modified parsed)
      {:statement {:update :feed
                   :set    {:feed/fetched-at now :feed/failed-syncs 0}
                   :where  [:= :feed/id feed-id]}
       :result    {:feed         (assoc feed :feed/fetched-at now)
                   :post-ids     (mapv :post/id existing)
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
            (if feed
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
                      :success  true}}))))

(defn load-existing
  [_ctx {:keys [url] :as input}]
  (assoc input :old-feed
         [:biff.graph.fx/query
          {:feed/url url}
          [:feed/id
           :feed/url
           :feed/created-at
           :feed/etag
           :feed/last-modified]]))

(defn fetch
  [_ctx {:keys [url old-feed data]}]
  (let [headers
        (cond-> {}
          (:feed/etag old-feed) (assoc "If-None-Match" (:feed/etag old-feed))
          (:feed/last-modified old-feed)
          (assoc "If-Modified-Since" (:feed/last-modified old-feed)))]
    {:url      url
     :data     data
     :old-feed old-feed
     :response [:biff.fx/http (request url headers)]}))

(defn load-canonical
  [_ctx {:keys [url old-feed response data]}]
  (let [parsed (parse-response url response)]
    {:old-feed old-feed
     :data     data
     :parsed   parsed

     :canonical
     [:biff.graph.fx/query
      {:feed/url (:url parsed)}
      [:feed/id
       :feed/url
       :feed/created-at
       :feed/etag
       :feed/last-modified]]}))

(defn load-posts
  [{:biff.fx/keys [now random-uuid7-seq]}
   {:keys [old-feed parsed canonical data]}]
  (let [feed (or (when (:feed/id canonical) canonical) old-feed)

        feed-id (or (:feed/id feed) (first random-uuid7-seq))]
    {:now    now
     :data   data
     :ids    random-uuid7-seq
     :feed   feed
     :parsed parsed

     :existing
     [:biff.graph.fx/query
      {:feed/id feed-id}
      [{:feed/posts
        [:post/id
         :post/content-id
         :post/guid
         :post/url
         :post/content-hash]}]]}))

(defn persist
  [_ctx {:keys [now ids feed parsed existing data]}]
  (let [{:keys [statement statements result]}
        (prepare-sync now ids feed (:feed/posts existing) parsed)

        write-statements (if statement [statement] statements)

        sync-result (assoc result :data data)]
    (if (:defer-write data)
      {:sync (assoc sync-result :write-statements write-statements)}
      {:sync   sync-result
       :_write (if statement
                 [:biff.sqlite.fx/execute statement]
                 [:biff.sqlite.fx/execute-tx statements])})))

(defn finish
  [_ctx state]
  (:sync state))

(def sync-fns
  [load-existing
   fetch
   load-canonical
   load-posts
   persist
   finish])
