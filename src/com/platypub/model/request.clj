(ns com.platypub.model.request
  (:require [com.biffweb.graph :refer [defresolver]]
            [clojure.string :as str]
            [com.platypub.lib.request :as request]
            [com.platypub.lib.tokens :as tokens]
            [com.platypub.schema :as schema])
  (:import [java.net URLDecoder]
           [java.nio.charset StandardCharsets]
           [java.util Base64]))

(defn- boolean-value [value]
  (or (true? value) (= "true" value)))

(defn- uuid-vector [value]
  (when (sequential? value)
    (let [values (remove #(and (string? %) (str/blank? %)) value)
          ids    (mapv request/uuid values)]
      (when (and (every? some? ids)
                 (= (count ids) (count (distinct ids))))
        ids))))

(defn- positive-int [value]
  (let [number (cond
                 (integer? value) value
                 (string? value) (parse-long value))]
    (when (and number (pos? number))
      number)))

(defn- tier-signal [user-id]
  (keyword "request" (str "tier-" user-id)))

(defn- data-url [value]
  (when (and (string? value) (str/starts-with? value "data:"))
    (let [[metadata encoded] (str/split value #"," 2)
          content-type       (some-> metadata str/lower-case)]
      (when-not (and encoded
                     (some #(str/starts-with? content-type %)
                           ["data:text/csv"
                            "data:application/csv"
                            "data:application/vnd.ms-excel"]))
        (throw (ex-info "Unsupported CSV data URL." {:metadata metadata})))
      (if (str/includes? metadata ";base64")
        (String. (.decode (Base64/getDecoder) ^String encoded)
                 StandardCharsets/UTF_8)
        (URLDecoder/decode encoded StandardCharsets/UTF_8)))))

(defn- upload-value [upload]
  (cond
    (and (sequential? upload) (= 1 (count upload)))
    (recur (first upload))

    (sequential? upload)
    (throw (ex-info "Expected exactly one uploaded CSV."
                    {:upload-count (count upload)}))

    (map? upload)
    (if-let [contents (:contents upload)]
      (let [mime (some-> (:mime upload) str/lower-case)]
        (when-not (#{"text/csv" "application/csv"
                     "application/vnd.ms-excel"} mime)
          (throw (ex-info "Unsupported uploaded CSV content type."
                          {:content-type mime})))
        (String. (.decode (Base64/getDecoder) ^String contents)
                 StandardCharsets/UTF_8))
      (or (:tempfile upload)
          (:file upload)
          (:content upload)
          (:data upload)
          (:body upload)))

    :else upload))

(defn- subscription-request [ctx]
  (let [email (request/text (request/value ctx :subscription/email))]
    (cond-> {:subscriber/email email}
      (or (request/value ctx :request/turnstile-token)
          (request/value ctx "cf-turnstile-response"))
      (assoc :request/turnstile-token
             (request/text
              (or (request/value ctx :request/turnstile-token)
                  (request/value ctx "cf-turnstile-response"))))
      (or (request/value ctx :request/hcaptcha-token)
          (request/value ctx "h-captcha-response"))
      (assoc :request/hcaptcha-token
             (request/text
              (or (request/value ctx :request/hcaptcha-token)
                  (request/value ctx "h-captcha-response")))))))

(def settings-fields
  [:publication/title
   :publication/description
   :publication/intro
   :publication/banner-image-url
   :publication/default-author-name
   :publication/default-author-url
   :publication/default-author-image-url
   :publication/padding-color
   :publication/background-color
   :publication/text-color
   :publication/primary-color
   :publication/filter-tag
   :publication/remove-tag
   :publication/welcome-html])

(defn- schema-map-query [map-schema]
  (mapv
   (fn [[attribute options value-schema]]
     (let [[options value-schema] (if (map? options)
                                    [options value-schema]
                                    [nil options])

           attribute
           (if (:optional options) [:? attribute] attribute)]
       (if (and (vector? value-schema) (= :map (first value-schema)))
         {attribute (schema-map-query value-schema)}
         attribute)))
   (rest map-schema)))

(def tab-state-query (schema-map-query schema/tab-state-schema))

(def tab-defaults
  {:tab/background-color :white})

(defn- publication-settings [ctx]
  (let [signals (:biff.datastar/signals ctx)]
    {:request/feed
     {:feed/url (request/text (request/value ctx :request/feed-url))}

     :request/publication-settings
     (merge
      (select-keys signals settings-fields)
      {:publication/automatic-sending
       (boolean-value (request/value ctx :publication/automatic-sending))

       :publication/require-confirmation
       (boolean-value (request/value ctx
                                     :publication/require-confirmation))})}))

(defresolver user
  {:output [{:request/user [:user/id]}]}
  [{:keys [session]} _]
  (when-let [user-id (request/uuid (:uid session))]
    {:request/user {:user/id user-id}}))

(defresolver publication-url
  {:output
   [{:request/new-publication
     [:publication/url [:? :publication/feed-url]]}]}
  [ctx _]
  (when-let [url (request/text (request/value ctx
                                              :request/publication-url))]
    {:request/new-publication
     (cond-> {:publication/url url}
       (request/value ctx :request/feed-url)
       (assoc :publication/feed-url
              (request/text (request/value ctx :request/feed-url))))}))

(defresolver publication
  {:output [{:request/publication [:publication/id]}]}

  (fn [{:keys [session] :as ctx} _]
    (when-let [user-id (request/uuid (:uid session))]
      (when-let [publication-id (request/path-uuid ctx :publication-id)]
        [:biff.sqlite.fx/execute
         {:select [:publication/id]
          :from   :publication
          :where  [:and
                   [:= :publication/id publication-id]
                   [:= :publication/user-id user-id]]}])))

  (fn [_ctx rows]
    (when-let [publication (first rows)]
      {:request/publication publication})))

(defresolver subscription-publication
  {:output
   [{:request/subscription-publication
     [:publication/id
      :publication/title
      [:? :publication/description]
      [:? :publication/intro]
      [:? :publication/banner-image-url]
      :publication/padding-color
      :publication/background-color
      :publication/text-color
      :publication/primary-color
      :publication/require-confirmation
      :publication/welcome-html]}]}

  [ctx _]
  (when-let [publication-id (request/path-uuid ctx :publication-id)]
    {:request/subscription-publication {:publication/id publication-id}}))

(defresolver tab-state
  {:output [{:request/tab tab-state-query}]}

  (fn [{:keys [biff.datastar/tab-id] :as ctx} _]
    (let [tab-id (or tab-id
                     (request/uuid
                      (request/value ctx :biff.datastar/client-tab-id)))]
      (when tab-id
        [:biff.sqlite.fx/execute
         {:select [:tab-state/data]
          :from   :tab-state
          :where  [:= :tab-state/id tab-id]}])))

  (fn [_ [{:tab-state/keys [data]}]]
    {:request/tab (merge tab-defaults data)}))

(defresolver pagination
  {:output [{:request/pagination [:page/number :page/limit :page/offset]}]}
  [ctx _]
  (let [number (or (positive-int (request/value ctx :page)) 1)
        limit  50]
    {:request/pagination
     {:page/number number
      :page/limit  limit
      :page/offset (* limit (dec number))}}))

(defresolver admin-publication-search
  {:input  [{:request/tab [[:? :tab/admin-publication-search]]}]
   :output [{:request/admin-publication-search [:publication/search]}]}
  [ctx input]
  {:request/admin-publication-search
   {:publication/search
    (or (when (request/signal-present? ctx :publication/search)
          (request/text (request/value ctx :publication/search)))
        (get-in input [:request/tab :tab/admin-publication-search])
        "")}})

(defresolver admin-publications
  {:input  [{:request/user [:user/tier]}
            {:request/admin-publication-search [:publication/search]}]
   :output [{:request/admin-publications [:publication/id]}]}
  (fn [_ctx input]
    (let [search (get-in input
                         [:request/admin-publication-search
                          :publication/search])]
      {:request/admin-publications
       (if (and (= :user.tier/admin
                   (get-in input [:request/user :user/tier]))
                (seq search))
         [:biff.sqlite.fx/execute
          {:select   [:publication/id]
           :from     :publication
           :join     [:user [:= :user/id :publication/user-id]]
           :where    [:or
                      [:like
                       [:lower :publication/title]
                       [:lower (str "%" search "%")]]
                      [:like
                       [:lower :user/email]
                       [:lower (str "%" search "%")]]]
           :order-by [[:publication/title :asc]]}]
         [])})))

(defresolver subscriber-search
  {:input  [{:request/tab
             [{[:? :tab/subscriber-search]
               [:publication/id :subscriber/search]}]}]
   :output [{:request/subscriber-search [:subscriber/search]}]}
  [ctx input]
  {:request/subscriber-search
   {:subscriber/search
    (or (when (request/signal-present? ctx :subscriber/search)
          (request/text (request/value ctx :subscriber/search)))
        (get-in input [:request/tab
                       :tab/subscriber-search
                       :subscriber/search])
        "")}})

(defresolver subscribers
  {:input  [{:request/publication [:publication/id]}
            {:request/subscriber-search [:subscriber/search]}
            {:request/pagination [:page/offset :page/limit]}]
   :output [{:request/subscribers [:subscriber/id]}]}
  (fn [_ctx input]
    (let [publication-id (get-in input [:request/publication :publication/id])
          search         (get-in input [:request/subscriber-search
                                        :subscriber/search])]
      {:request/subscribers
       [:biff.sqlite.fx/execute
        (cond-> {:select   [:subscriber/id]
                 :from     :subscriber
                 :where    [:= :subscriber/publication-id publication-id]
                 :order-by [[:subscriber/subscribed-at :desc]]
                 :limit    (get-in input [:request/pagination :page/limit])
                 :offset   (get-in input [:request/pagination :page/offset])}
          (seq search)
          (assoc :where [:and
                         [:= :subscriber/publication-id publication-id]
                         [:like :subscriber/email (str "%" search "%")]]))]})))

(defresolver subscriber
  {:input  [{:request/user [:user/id]}]
   :output [{:request/subscriber [:subscriber/id]}]}

  (fn [ctx input]
    (let [subscriber-id (request/path-uuid ctx :subscriber-id)
          user-id       (get-in input [:request/user :user/id])]
      (when (and subscriber-id user-id)
        {:subscriber-id subscriber-id

         :subscriber
         [:biff.sqlite.fx/execute
          {:select [:subscriber/id]
           :from   :subscriber
           :join   [:publication
                    [:= :publication/id :subscriber/publication-id]]
           :where  [:and
                    [:= :subscriber/id subscriber-id]
                    [:= :publication/user-id user-id]]}]})))

  (fn [_ {:keys [subscriber-id subscriber]}]
    (when (and subscriber-id
               (= subscriber-id (:subscriber/id (first subscriber))))
      {:request/subscriber {:subscriber/id subscriber-id}})))

(defresolver subscription
  {:output [{:request/subscription
             [:subscriber/email
              [:? :request/turnstile-token]
              [:? :request/hcaptcha-token]]}]}
  [ctx _]
  {:request/subscription (subscription-request ctx)})

(defresolver confirmation-token
  {:output [{:request/confirmation [:request/token]}]}
  [ctx _]
  (when-let [token (request/text (request/value ctx :token))]
    (try
      {:request/confirmation
       {:request/token (.decode (Base64/getUrlDecoder) ^String token)}}
      (catch IllegalArgumentException _ nil))))

(defresolver unsubscribe-claims
  {:output [{:request/unsubscribe-claims
             [:subscriber/id
              :subscriber/email
              :request/expiration
              :request/token]}]}
  [ctx _]
  (when-let [token (request/text (request/value ctx :token))]
    (let [claims (tokens/process
                  :unsign
                  (force (:platypub/unsubscribe-secret ctx))
                  token)
          id     (request/uuid (:subscriber-id claims))
          email  (request/text (:email claims))
          exp    (:exp claims)]
      (when (and id email (integer? exp))
        {:request/unsubscribe-claims
         {:subscriber/id      id
          :subscriber/email   email
          :request/expiration exp
          :request/token      token}}))))

(defresolver admin-user-tier
  {:input  [{:request/user [:user/tier]}]
   :output [{:request/admin-user-tier [:user/id :user/tier :request/tier]}]}

  (fn [ctx input]
    (when-let [user-id (and (= :user.tier/admin
                               (get-in input [:request/user :user/tier]))
                            (request/path-uuid ctx :user-id))]
      {:user-id user-id
       :tier    (request/text (request/value ctx (tier-signal user-id)))

       :user
       [:biff.sqlite.fx/execute
        {:select [:user/id :user/tier]
         :from   :user
         :where  [:= :user/id user-id]}]}))

  (fn [_ {:keys [user-id tier user]}]
    (when-let [user (first user)]
      {:request/admin-user-tier
       {:user/id      user-id
        :user/tier    (:user/tier user)
        :request/tier tier}})))

(defresolver admin-publication-import
  {:input  [{:request/user [:user/tier]}]
   :output [{:request/admin-publication-import [:publication/id :request/csv]}]}

  (fn [ctx input]
    (when-let [publication-id
               (and (= :user.tier/admin
                       (get-in input [:request/user :user/tier]))
                    (request/path-uuid ctx :publication-id))]
      (when-let [upload (request/value ctx :request/csv)]
        (let [upload (upload-value upload)]
          (when-not upload
            (throw (ex-info "Uploaded CSV is missing its content." {})))
          {:publication-id publication-id
           :csv            (or (data-url upload)
                               (when (string? upload) upload)
                               [:platypub/read-uploaded-file upload])

           :publication
           [:biff.sqlite.fx/execute
            {:select [:publication/id]
             :from   :publication
             :where  [:= :publication/id publication-id]}]}))))

  (fn [_ {:keys [publication-id csv publication]}]
    (when (and publication-id
               (= publication-id (:publication/id (first publication))))
      {:request/admin-publication-import
       {:publication/id publication-id
        :request/csv    csv}})))

(defresolver publication-settings-request
  {:output [{:request/feed [:feed/url]}
            {:request/publication-settings
             [:publication/title
              [:? :publication/description]
              [:? :publication/intro]
              [:? :publication/banner-image-url]
              [:? :publication/default-author-name]
              [:? :publication/default-author-url]
              [:? :publication/default-author-image-url]
              :publication/padding-color
              :publication/background-color
              :publication/text-color
              :publication/primary-color
              [:? :publication/filter-tag]
              [:? :publication/remove-tag]
              :publication/welcome-html
              :publication/automatic-sending
              :publication/require-confirmation]}]}
  [ctx _]
  (publication-settings ctx))

(defresolver send-selection
  {:input  [{:request/publication [:publication/id]}
            {:request/tab
             [{[:? :tab/send-preview]
               [:publication/id :send/post-ids]}]}]
   :output [{:request/send-selection [[:? :send/post-ids]]}]}

  (fn [ctx input]
    (let [publication-id (get-in input [:request/publication :publication/id])
          post-ids       (if (request/signal-present? ctx :send/post-ids)
                           (request/value ctx :send/post-ids)
                           (get-in input [:request/tab
                                          :tab/send-preview
                                          :send/post-ids]))
          post-ids       (or (uuid-vector post-ids) [])]
      {:post-ids post-ids

       :posts
       (when (seq post-ids)
         [:biff.sqlite.fx/execute
          {:select [:post/id]
           :from   :post
           :join   [[:publication :publication]
                    [:= :publication/feed-id :post/feed-id]]

           :left-join
           [[:send-post :send-post]
            [:= :send-post/post-id :post/id]
            [:send :send]
            [:and
             [:= :send/id :send-post/send-id]
             [:= :send/publication-id publication-id]]]

           :where [:and
                   [:in :post/id post-ids]
                   [:= :publication/id publication-id]
                   [:>=
                    :post/present-as-of
                    :publication/feed-id-updated-at]
                   [:is :send/id nil]]}])}))

  (fn [_ {:keys [post-ids posts]}]
    {:request/send-selection
     (cond-> {}
       (and (seq post-ids)
            (= (set post-ids) (set (map :post/id posts))))
       (assoc :send/post-ids post-ids))}))

(defresolver send-posts
  {:input  [{:request/send-selection [[:? :send/post-ids]]}]
   :output [{:request/send-posts [:post/id]}]}
  [_ input]
  {:request/send-posts
   (mapv #(hash-map :post/id %)
         (get-in input [:request/send-selection :send/post-ids]))})

(defresolver send-preview
  {:input  [{:request/publication [:publication/id]}
            {:request/tab
             [{[:? :tab/send-preview]
               [:publication/id
                :send/subject
                :send/html
                :send/text
                :send/from-name
                :send/reply-to
                :send/post-ids]}]}]
   :output [{:request/send-preview
             [:send/subject :send/html :send/text :send/from-name :send/reply-to
              {:send/posts [:post/id]}]}]}
  [_ input]
  (let [publication-id (get-in input [:request/publication :publication/id])
        preview        (get-in input [:request/tab :tab/send-preview])]
    (when (= publication-id (:publication/id preview))
      {:request/send-preview
       {:send/subject   (:send/subject preview)
        :send/html      (:send/html preview)
        :send/text      (:send/text preview)
        :send/from-name (:send/from-name preview)
        :send/reply-to  (:send/reply-to preview)
        :send/posts     (mapv (fn [post-id] {:post/id post-id})
                              (:send/post-ids preview))}})))

(def module
  {:biff.graph/resolvers
   [user
    publication-url
    publication
    subscription-publication
    tab-state
    pagination
    admin-publication-search
    admin-publications
    subscriber-search
    subscribers
    subscriber
    subscription
    confirmation-token
    unsubscribe-claims
    admin-user-tier
    admin-publication-import
    publication-settings-request
    send-selection
    send-posts
    send-preview]})
