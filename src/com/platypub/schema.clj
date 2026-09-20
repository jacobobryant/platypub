(ns com.platypub.schema
  (:require [com.biffweb.sqlite :as sqlite]))

(def ? {:optional true})

(defn base [args]
  (let [[flags args] ((juxt filterv remove) #{:required :unique :index} args)

        opts (apply hash-map args)]
    (into opts (zipmap flags (repeat true)))))

(def primary-key {:type :uuid :primary-key true})

(defn ref* [target & args]
  (assoc (base args) :ref target :type :uuid))

(defn enum [enum-values & args]
  (assoc (base args) :type :enum :enum-values enum-values))

(defn edn* [extra-schema & args]
  (assoc (base args) :type :edn :extra-schema extra-schema))

(defn text [& args] (assoc (base args) :type :text))
(defn inst [& args] (assoc (base args) :type :inst))
(defn int* [& args] (assoc (base args) :type :int))
(defn bool [& args] (assoc (base args) :type :boolean))
(defn blob [& args] (assoc (base args) :type :blob))

(def tab-state-schema
  [:map
   [:tab/background-color ? [:enum :white :red :blue :green]]
   [:tab/admin-publication-search ? :string]
   [:tab/subscriber-search
    ?
    [:map
     [:publication/id :uuid]
     [:subscriber/search :string]]]
   [:tab/send-preview
    ?
    [:map
     [:publication/id :uuid]
     [:send/subject :string]
     [:send/html :string]
     [:send/post-ids [:vector :uuid]]]]])

(def columns
  {:tab-state/id   primary-key
   :tab-state/data (edn* tab-state-schema)

   :user/id           primary-key
   :user/email        (text :required :unique)
   :user/joined-at    (inst :required :index)
   :user/tier         (enum {0 :user.tier/waitlist
                             1 :user.tier/free
                             2 :user.tier/admin})
   :user/display-name (text)

   :content/id   primary-key
   :content/data (edn* :string :required)

   :feed/id            primary-key
   :feed/created-at    (inst :required)
   :feed/fetched-at    (inst :required :index)
   :feed/url           (text :required :unique)
   :feed/failed-syncs  (int* :required)
   :feed/etag          (text)
   :feed/last-modified (text)

   :post/id               primary-key
   :post/feed-id          (ref* :feed/id :required :index)
   :post/fetched-at       (inst :required :index)
   :post/present-as-of    (inst :required)
   :post/guid             (text)
   :post/published-at     (inst)
   :post/title            (text)
   :post/url              (text)
   :post/content-id       (ref* :content/id)
   :post/content-hash     (text)
   :post/tags             (edn* :any)
   :post/author-name      (text)
   :post/author-url       (text)
   :post/author-image-url (text)
   :post/length           (int*)
   :post/excerpt          (text)

   :publication/id                       primary-key
   :publication/created-at               (inst :required)
   :publication/user-id                  (ref* :user/id :required :index)
   :publication/feed-id                  (ref* :feed/id :required :index)
   :publication/feed-id-updated-at       (inst :required)
   :publication/title                    (text :required)
   :publication/padding-color            (text :required)
   :publication/background-color         (text :required)
   :publication/text-color               (text :required)
   :publication/primary-color            (text :required)
   :publication/welcome-html             (text :required)
   :publication/require-confirmation     (bool :required)
   :publication/description              (text)
   :publication/intro                    (text)
   :publication/banner-image-url         (text)
   :publication/default-author-name      (text)
   :publication/default-author-url       (text)
   :publication/default-author-image-url (text)
   :publication/filter-tag               (text)
   :publication/remove-tag               (text)
   :publication/automatic-send-threshold (inst)

   :subscriber/id                        primary-key
   :subscriber/email                     (text :required :index :unique-with
                                               [:subscriber/publication-id])
   :subscriber/publication-id            (ref* :publication/id :required :index)
   :subscriber/subscribed-at             (inst :required :index)
   :subscriber/require-confirmation      (bool :required)
   :subscriber/confirmation-triggered-at (inst)
   :subscriber/confirmation-token        (blob)
   :subscriber/confirmed-at              (inst)
   :subscriber/headers                   (edn* :any)
   :subscriber/form-params               (edn* :any)
   :subscriber/query-params              (edn* :any)
   :subscriber/unsubscribed-at           (inst)
   :subscriber/suppressed                (bool)

   :send/id             primary-key
   :send/publication-id (ref* :publication/id :required :index)
   :send/started-at     (inst :required)
   :send/progress-at    (inst :required :index)
   :send/status         (enum {0 :send.status/pending
                               1 :send.status/finished}
                              :required)
   :send/from-name      (text :required)
   :send/subject        (text :required)
   :send/content-id     (ref* :content/id :required)
   :send/provenance     (enum {0 :send.provenance/manual
                               1 :send.provenance/automatic}
                              :required)

   :send-post/id      primary-key
   :send-post/send-id (ref* :send/id :required :index)
   :send-post/post-id (ref* :post/id :required)

   :send-attempt/id            primary-key
   :send-attempt/send-id       (ref* :send/id :required :index :unique-with
                                     [:send-attempt/subscriber-id])
   :send-attempt/subscriber-id (ref* :subscriber/id :required)})

;; Strings added here will be appended to resources/schema.sql
(def extra-init-sql
  [(str "CREATE INDEX IF NOT EXISTS idx_post_feed_sort "
        "ON post(feed_id, fetched_at, published_at);")
   (str
    "CREATE INDEX IF NOT EXISTS idx_subscriber_publication_date "
    "ON subscriber(publication_id, subscribed_at);")
   (str
    "CREATE INDEX IF NOT EXISTS idx_send_publication_date "
    "ON send(publication_id, started_at);")
   (str
    "CREATE TRIGGER IF NOT EXISTS prevent_duplicate_publication_post "
    "BEFORE INSERT ON send_post WHEN EXISTS (SELECT 1 FROM send_post previous "
    "JOIN send previous_send ON previous_send.id = previous.send_id "
    "JOIN send new_send ON new_send.id = NEW.send_id "
    "WHERE previous.post_id = NEW.post_id "
    "AND previous_send.publication_id = new_send.publication_id) "
    "BEGIN SELECT RAISE(ABORT, 'post already sent for publication'); END;")])

;; Add new columns here that you want users to be able to edit (e.g. settings).
(def editable-user-fields [:user/display-name])

(def editable-publication-fields
  [:publication/feed-id
   :publication/feed-id-updated-at
   :publication/title
   :publication/padding-color
   :publication/background-color
   :publication/text-color
   :publication/primary-color
   :publication/welcome-html
   :publication/require-confirmation
   :publication/automatic-send-threshold
   :publication/description
   :publication/intro
   :publication/banner-image-url
   :publication/default-author-name
   :publication/default-author-url
   :publication/default-author-image-url
   :publication/filter-tag
   :publication/remove-tag])

(defn only-fields-edited?
  [before after fields]
  (= (apply dissoc (or before {}) fields)
     (apply dissoc (or after {}) fields)))

(defn- entry-id
  [before after attribute]
  (or (get after attribute) (get before attribute)))

(defn- query
  [ctx connection statement]
  (sqlite/execute
   (assoc ctx
          :biff.sqlite/read-pool connection
          :biff.sqlite/write-conn connection)
   statement))

(defn- exists?
  [ctx connection statement]
  (boolean (seq (query ctx connection statement))))

(defn- current-user-id
  [ctx]
  (get-in ctx [:session :uid]))

(defn- admin?
  [ctx]
  (when-let [user-id (current-user-id ctx)]
    (= :user.tier/admin
       (:user/tier
        (first
         (query
          ctx
          (:biff.sqlite/before-conn ctx)
          {:select [:user/tier]
           :from   :user
           :where  [:= :user/id user-id]}))))))

(defn- owns-publication?
  [ctx publication-id]
  (when-let [user-id (current-user-id ctx)]
    (exists?
     ctx
     (:biff.sqlite/before-conn ctx)
     {:select [:publication/id]
      :from   :publication
      :where  [:and
               [:= :publication/id publication-id]
               [:= :publication/user-id user-id]]})))

(defn- owns-feed?
  [ctx feed-id]
  (when-let [user-id (current-user-id ctx)]
    (exists?
     ctx
     (:biff.sqlite/after-conn ctx)
     {:select [:feed/id]
      :from   :feed
      :join   [:publication [:= :publication/feed-id :feed/id]]
      :where  [:and
               [:= :feed/id feed-id]
               [:= :publication/user-id user-id]]})))

(defn- owns-subscriber?
  [ctx subscriber-id]
  (when-let [user-id (current-user-id ctx)]
    (exists?
     ctx
     (:biff.sqlite/before-conn ctx)
     {:select [:subscriber/id]
      :from   :subscriber
      :join   [:publication
               [:= :publication/id :subscriber/publication-id]]
      :where  [:and
               [:= :subscriber/id subscriber-id]
               [:= :publication/user-id user-id]]})))

(defn- owns-post?
  [ctx post-id]
  (when-let [user-id (current-user-id ctx)]
    (exists?
     ctx
     (:biff.sqlite/after-conn ctx)
     {:select [:post/id]
      :from   :post
      :join   [:publication
               [:= :publication/feed-id :post/feed-id]]
      :where  [:and
               [:= :post/id post-id]
               [:= :publication/user-id user-id]]})))

(defn- owns-send?
  [ctx send-id]
  (when-let [user-id (current-user-id ctx)]
    (exists?
     ctx
     (:biff.sqlite/after-conn ctx)
     {:select [:send/id]
      :from   :send
      :join   [:publication
               [:= :publication/id :send/publication-id]]
      :where  [:and
               [:= :send/id send-id]
               [:= :publication/user-id user-id]]})))

(defn- owns-content?
  [ctx content-id]
  (when-let [user-id (current-user-id ctx)]
    (or
     (exists?
      ctx
      (:biff.sqlite/after-conn ctx)
      {:select [:content/id]
       :from   :content
       :join   [:send
                [:= :send/content-id :content/id]
                :publication
                [:= :publication/id :send/publication-id]]
       :where  [:and
                [:= :content/id content-id]
                [:= :publication/user-id user-id]]})
     (exists?
      ctx
      (:biff.sqlite/after-conn ctx)
      {:select [:content/id]
       :from   :content
       :join   [:post
                [:= :post/content-id :content/id]
                :publication
                [:= :publication/feed-id :post/feed-id]]
       :where  [:and
                [:= :content/id content-id]
                [:= :publication/user-id user-id]]}))))

(defn- owns-send-post?
  [ctx send-post-id]
  (when-let [user-id (current-user-id ctx)]
    (exists?
     ctx
     (:biff.sqlite/after-conn ctx)
     {:select [:send-post/id]
      :from   :send-post
      :join   [:send
               [:= :send/id :send-post/send-id]
               :publication
               [:= :publication/id :send/publication-id]]
      :where  [:and
               [:= :send-post/id send-post-id]
               [:= :publication/user-id user-id]]})))

(defn authorize-entry
  [ctx {:keys [table op before after]}]
  (case table
    :user
    (let [user-id (entry-id before after :user/id)]
      (case op
        :create false
        :update (or (and (admin? ctx)
                         (only-fields-edited? before after [:user/tier]))
                    (and (= user-id (current-user-id ctx))
                         (only-fields-edited?
                          before after editable-user-fields)))
        :delete (= user-id (current-user-id ctx))))

    :publication
    (let [publication-id (entry-id before after :publication/id)]
      (case op
        :create (= (:publication/user-id after) (current-user-id ctx))
        :update (and (owns-publication? ctx publication-id)
                     (only-fields-edited?
                      before after editable-publication-fields))
        false))

    :feed
    (and (#{:create :update} op)
         (owns-feed? ctx (entry-id before after :feed/id)))

    :post
    (and (#{:create :update} op)
         (owns-post? ctx (entry-id before after :post/id)))

    :subscriber
    (let [subscriber-id (entry-id before after :subscriber/id)]
      (or
       (admin? ctx)
       (and (= op :update)
            (owns-subscriber? ctx subscriber-id)
            (only-fields-edited?
             before after [:subscriber/unsubscribed-at]))))

    :content
    (and (#{:create :update} op)
         (owns-content? ctx (:content/id after)))

    :send (and (= op :create) (owns-send? ctx (:send/id after)))

    :send-post
    (and (= op :create)
         (owns-send-post? ctx (entry-id before after :send-post/id)))

    false))

;; These authorization rules apply when using biff.sqlite/authorized-write and
;; are meant as an extra layer of protection.
(defn authorize [ctx diff]
  (every? #(authorize-entry ctx %) diff))
