(ns com.platypub.schema)

(def ? {:optional true})

(defn base [args]
  (let [[flags args] ((juxt filterv remove) #{:required :unique :index} args)
        opts         (apply hash-map args)]
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
   [:tab/background-color ? [:enum :white :red :blue :green]]])

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
   :subscriber/email                     (text :required :index
                                               {:unique-with
                                                [:subscriber/publication-id]})
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
   :send-attempt/send-id       (ref* :send/id :required :index
                                     {:unique-with
                                      [:send-attempt/subscriber-id]})
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
  (= (apply dissoc before fields)
     (apply dissoc after fields)))

(defn authorize-entry
  [{{:keys [uid]} :session,
    :keys         [biff.datastar/tab-id
                   platypub/user
                   platypub/authorized-publication-id
                   platypub/authorized-subscriber-id]}
   {:keys [table op before after]}]
  (let [admin? (= :user.tier/admin (:user/tier user))]
    (case table
      :user (case op
              :create false
              :update (or (and admin?
                               (only-fields-edited?
                                before after [:user/tier]))
                          (and (every? #{uid}
                                       (keep :user/id [before after]))
                               (only-fields-edited?
                                before after editable-user-fields)))
              :delete (every? #{uid} (keep :user/id [before after])))
      :tab-state (every? #{tab-id}
                         (keep :tab-state/id [before after]))
      :publication
      (and (every? #{uid} (keep :publication/user-id [before after]))
           (or (= op :create)
               (and (= op :update)
                    (only-fields-edited? before after
                                         editable-publication-fields))))
      :subscriber
      (or admin?
          (and authorized-publication-id
               (every? #{authorized-publication-id}
                       (keep :subscriber/publication-id [before after]))
               (= op :update)
               (only-fields-edited?
                before after [:subscriber/unsubscribed-at]))
          (and authorized-subscriber-id
               (every? #{authorized-subscriber-id}
                       (keep :subscriber/id [before after]))
               (= op :update)
               (only-fields-edited?
                before after
                [:subscriber/confirmation-token
                 :subscriber/confirmation-triggered-at
                 :subscriber/unsubscribed-at])))
      false)))

;; These authorization rules apply when using biff.sqlite/authorized-write and
;; are meant as an extra layer of protection.
(defn authorize [ctx diff]
  (every? #(authorize-entry ctx %) diff))
