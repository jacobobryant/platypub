(ns com.platypub.app.publications.publication.settings
  (:require [clojure.string :as str]
            [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.feed :as feed]
            [com.platypub.lib.middleware :as mid]
            [com.platypub.lib.request :as request]
            [com.platypub.lib.tab :as tab]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]))

(defpath preview-path
  "/app/publications/:publication-id/settings/preview/:kind")
(defpath close-preview-path
  "/app/publications/:publication-id/settings/close-preview")
(defpath image-path "/app/publications/:publication-id/settings/image/:field")

(def setting-fields
  [[:publication/title "Title" "text"]
   [:publication/address "Address" "text"]
   [:publication/archive-url "Archive URL" "url"]
   [:publication/description "Description" "text"]
   [:publication/form-placeholder "Form placeholder" "text"]
   [:publication/intro "Intro" "text"]
   [:publication/site-url "Website URL" "url"]
   [:publication/default-author-name
    "Default author name"
    "text"]
   [:publication/default-author-url
    "Default author URL"
    "url"]
   [:publication/padding-color "Padding color" "color"]
   [:publication/background-color
    "Background color"
    "color"]
   [:publication/text-color "Text color" "color"]
   [:publication/primary-color "Primary color" "color"]
   [:publication/filter-tag "Filter tag" "text"]
   [:publication/remove-tag "Remove tag" "text"]])

(defn- setting-value
  [field value]
  (let [value (str/trim (or value ""))]
    (not-empty
     (if (= field :publication/address)
       (str/replace value #"[\r\n]+" " ")
       value))))

(defn- settings-signals
  [publication feed-url]
  (merge
   (select-keys publication
                (concat (map first setting-fields)
                        [:publication/banner-image-url
                         :publication/default-author-image-url]))
   {:request/feed-url        feed-url
    :publication/form-style  (name (or (:publication/form-style publication)
                                       :publication.form-style/rectangle))
    :publication/email-style (name (or (:publication/email-style publication)
                                       :publication.email-style/card))

    :publication/hide-form-title
    (boolean (:publication/hide-form-title publication))

    :publication/welcome-html (:publication/welcome-html publication)

    :publication/automatic-sending
    (some? (:publication/automatic-send-threshold publication))

    :publication/require-confirmation
    (:publication/require-confirmation publication)}))

(defn- pending-images
  [publication tab-state]
  (let [images (:tab/publication-images tab-state)]
    (when (= (:publication/id publication) (:publication/id images))
      images)))

(def settings-tab-query
  [{[:? :tab/publication-images]
    [:publication/id
     [:? :publication/banner-image-url]
     [:? :publication/default-author-image-url]]}
   {[:? :tab/settings-preview]
    [:publication/id
     :settings/kind
     [:? :settings/open]
     [:? :settings/request-id]
     [:? :settings/html]
     {:settings/values
      [:publication/title
       [:? :publication/address]
       [:? :publication/archive-url]
       [:? :publication/description]
       [:? :publication/hide-form-title]
       [:? :publication/form-placeholder]
       [:? :publication/form-style]
       [:? :publication/intro]
       [:? :publication/site-url]
       [:? :publication/email-style]
       [:? :publication/banner-image-url]
       [:? :publication/default-author-name]
       [:? :publication/default-author-url]
       [:? :publication/default-author-image-url]
       :publication/padding-color
       :publication/background-color
       :publication/text-color
       :publication/primary-color]}]}])

(defn- preview-posts
  [kind now ids]
  (let [paragraphs
        [(str "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
              "Integer vitae nibh vitae nisi facilisis finibus. Sed euismod, "
              "lectus sed varius posuere, sapien purus vulputate nibh, sit "
              "amet pretium augue felis vitae turpis. Curabitur interdum "
              "massa at justo feugiat, sed blandit dolor faucibus.")
         (str "Praesent aliquam magna in nibh tempor, vel porttitor neque "
              "pulvinar. Donec sit amet lectus non justo faucibus accumsan. "
              "Mauris tincidunt sem at nibh vestibulum, a viverra lorem "
              "fermentum. Vivamus dignissim libero vel ante sollicitudin, "
              "quis posuere mi finibus.")
         (str "Suspendisse potenti. Aliquam erat volutpat. Nulla facilisi. "
              "Proin faucibus, felis eget sodales tristique, risus ex "
              "ultrices risus, et tincidunt dui sem quis lacus. Aenean "
              "consequat nibh et massa tincidunt, sit amet vestibulum "
              "augue luctus.")]

        plain (str/join "\n\n" paragraphs)
        html  (str "<p>" (str/join "</p><p>" paragraphs) "</p>")
        post  (fn [id title]
                {:post/id         id
                 :post/title      title
                 :post/fetched-at now
                 :post/url        "https://example.com/post"
                 :post/excerpt    (subs plain 0 (min 500 (count plain)))

                 :post/content
                 {:content/html html
                  :content/text plain}})]
    (cond-> [(post (first ids) "Example post")]
      (= kind "multi")
      (conj (post (second ids) "Another example post")))))

(defpipeline settings-page
  [:biff.graph.fx/query
   [{[:? :request/publication]
     [:publication/id
      :publication/title
      [:? :publication/address]
      [:? :publication/archive-url]
      [:? :publication/description]
      [:? :publication/hide-form-title]
      [:? :publication/form-placeholder]
      [:? :publication/form-style]
      [:? :publication/intro]
      [:? :publication/site-url]
      [:? :publication/email-style]
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
      [:? :publication/automatic-send-threshold]
      :publication/require-confirmation
      {:publication/feed [:feed/url]}]}
    {:request/tab settings-tab-query}]]

  (fn [request result]
    (if-let [publication (:request/publication result)]
      (let [publication  (merge publication
                                (pending-images publication
                                                (:request/tab result)))
            feed-url     (get-in publication [:publication/feed :feed/url])
            stored       (get-in result [:request/tab :tab/settings-preview])
            preview      (when (= (:publication/id stored)
                                  (:publication/id publication))
                           stored)
            preview-kind (:settings/kind preview)
            preview-html (:settings/html preview)
            preview-open (true? (:settings/open preview))]
        (ui/app-shell
         request
         [:main
          {:data-signals
           (datastar/signals-json
            {:settings/archiveopen false})

           :class ["mx-auto w-full max-w-5xl p-6"]}
          (ui/publication-header publication :settings)
          [:div
           {:class ["my-6 grid gap-4 sm:grid-cols-2"]}
           (for [[field label image]
                 [["banner" "Banner image"
                   (:publication/banner-image-url publication)]
                  ["author" "Default author image"
                   (:publication/default-author-image-url publication)]]]
             [:form
              {:data-on:change
               "@post(el.dataset.action, {contentType: 'form'})"

               :data-action (image-path (:publication/id publication) field)
               :enctype     "multipart/form-data"
               :class       ["rounded border p-4"]}
              [:div {:class ["font-medium"]} label]
              (when image
                [:img {:src image :class ["my-2 max-h-32 max-w-full"]}])
              [:input
               {:type            "hidden"
                :name            (datastar/signal-name
                                  :biff.datastar/client-tab-id)
                :data-attr:value "$biff_datastar_client-tab-id"}]
              [:input {:type     "file"
                       :name     (datastar/signal-name :request/image)
                       :accept   "image/png,image/jpeg,image/gif,image/webp"
                       :required true
                       :class    ["mt-2 w-full rounded border border-border"
                                  "bg-surface p-2"]}]])]
          [:div
           {:class ["grid gap-8"]}
           [:form
            {:data-on:submit "@post(el.dataset.action)",

             :data-action
             (routes/publication-settings (:publication/id publication)),

             :data-signals__ifmissing
             (datastar/signals-json (settings-signals publication feed-url)),

             :class ["grid gap-4"]}
            [:label
             "Feed URL"
             [:input
              {:data-bind (datastar/signal-name :request/feed-url),
               :name      (datastar/signal-name :request/feed-url),
               :type      "url",
               :required  true,

               :class ["mt-1 block w-full rounded border p-2"]}]]
            (for [[field label input-type] setting-fields]
              (let [signal (datastar/signal-name field)]
                (if (= input-type "color")
                  (let [label-id  (str signal "-label")
                        picker-id (str signal "-picker")]
                    [:div
                     [:label {:id label-id :for picker-id} label]
                     [:div {:class ["mt-1 flex items-center gap-2"]}
                      [:input {:id        picker-id
                               :data-bind signal
                               :name      signal
                               :type      "color"
                               :class     ["h-12 w-20 rounded border p-1"]}]
                      [:input {:data-bind        signal
                               :type             "text"
                               :aria-label       "Hex value"
                               :aria-describedby label-id
                               :placeholder      "#RRGGBB"
                               :pattern          "#[0-9a-fA-F]{6}"
                               :maxlength        7
                               :required         true
                               :spellcheck       false
                               :class            ["h-12 w-32 rounded border"
                                                  "p-2 font-mono"]}]]])
                  [:label
                   label
                   [:input {:data-bind signal
                            :name      signal
                            :type      input-type
                            :class     ["mt-1 block w-full rounded border"
                                        "p-2"]}]])))
            [:label
             {:class ["flex gap-2"]}
             [:input
              {:data-bind (datastar/signal-name :publication/hide-form-title)
               :type      "checkbox"}]
             "Hide form title"]
            [:label "Form style"
             [:select
              {:data-bind (datastar/signal-name :publication/form-style)
               :name      (datastar/signal-name :publication/form-style)
               :class     ["mt-1 block w-full rounded border p-2"]}
              [:option {:value "rectangle"} "Rectangle"]
              [:option {:value "pill"} "Pill"]]]
            [:label "Email style"
             [:select
              {:data-bind (datastar/signal-name :publication/email-style)
               :name      (datastar/signal-name :publication/email-style)
               :class     ["mt-1 block w-full rounded border p-2"]}
              [:option {:value "card"} "Card"]
              [:option {:value "letter"} "Letter"]]]
            [:label
             {:class ["flex gap-2"]}
             [:input
              {:data-bind (datastar/signal-name :publication/automatic-sending),

               :type "checkbox"}] "Automatic sending"]
            [:label
             {:class ["flex gap-2"]}
             [:input
              {:data-bind
               (datastar/signal-name :publication/require-confirmation),

               :type "checkbox"}]
             "Require confirmation"]
            [:label
             "Welcome HTML"
             [:textarea
              {:data-bind (datastar/signal-name :publication/welcome-html),
               :name      (datastar/signal-name :publication/welcome-html),

               :class ["mt-1 h-32 w-full rounded border p-2"]}]]
            [:div
             [:button {:class ["rounded bg-primary px-4 py-2 text-white"]}
              "Save settings"]]]
           [:section {:class ["rounded border border-border bg-surface p-5"]}
            [:h2 {:class ["text-xl font-semibold"]} "Preview"]
            [:div {:class ["mt-4 flex flex-wrap items-center gap-3"]}
             (mapv
              (fn [[kind label]]
                [:button
                 {:type "button"

                  :data-on:click
                  (str "@post('"
                       (preview-path (:publication/id publication) kind)
                       "')")

                  :class ["rounded border border-border px-4 py-2"
                          "text-primary hover:bg-background"]}
                 label])
              [["form" "Preview subscribe form"]
               ["one" "Preview email (one post)"]
               ["multi" "Preview email (multiple posts)"]])]]
           [:section {:class ["rounded border border-border bg-surface p-5"]}
            [:h2 {:class ["text-xl font-semibold"]} "Archive publication"]
            [:p {:class ["my-4"]}
             (str "If you archive this publication, the subscribe forms "
                  "will be disabled and no emails will be sent to existing "
                  "subscribers.")]
            [:button {:type "button"

                      :data-on:click
                      "$settings_archiveopen = true"

                      :class ["rounded border border-border px-4 py-2"]}
             "Archive publication"]]
           [:div
            (ui/modal
             {:id    "settings-preview"
              :open? preview-open
              :class ["w-full max-w-3xl rounded border border-border p-0"
                      "bg-surface shadow-xl"]}
             nil
             (str "@post('" (close-preview-path
                             (:publication/id publication)) "')")
             (if preview-html
               [:iframe {:title   "Settings preview"
                         :srcdoc  preview-html
                         :style   {:height (str "min("
                                                (if (= preview-kind "form")
                                                  "384px" "720px")
                                                ", calc(100svh - 8rem))")}
                         :sandbox "allow-same-origin"
                         :class   ["block w-full border-0"]}]
               [:div {:role  "status"
                      :class ["p-8 text-center"]}
                "Preparing preview…"])
             [:button {:type          "button"
                       :data-on:click (str "@post('" (close-preview-path
                                                      (:publication/id
                                                       publication)) "')")
                       :class         ["m-4 text-primary"]} "Close"])
            (ui/modal
             {:id    "archive-publication"
              :class ["w-full max-w-md rounded border border-border bg-surface"
                      "p-6 shadow-xl"]}
             "$settings_archiveopen"
             "$settings_archiveopen = false"
             [:h2 {:class ["text-xl font-semibold"]} "Archive publication?"]
             [:p {:class ["my-4"]}
              (str "Subscribers will no longer be able to subscribe and "
                   "sending will stop.")]
             [:div {:class ["flex justify-end gap-2"]}
              [:button {:type "button"

                        :data-on:click
                        "$settings_archiveopen = false"

                        :class
                        ["rounded border border-border px-4 py-2"]}
               "Cancel"]
              [:form
               {:data-on:submit
                (str "@post(el.dataset.action).then(() => "
                     "window.location.href='" (routes/app) "')")

                :data-action             (routes/archive-publication
                                          (:publication/id publication))
                :data-signals__ifmissing (datastar/signals-json {})}
               [:button {:class ["rounded bg-primary px-4 py-2 text-white"]}
                "Archive"]]])]]]))
      {:status 404})))

(defpipeline settings-preview
  [:biff.graph.fx/query
   [{[:? :request/publication] [:publication/id]}
    :request/preview-kind
    {:request/publication-settings
     [:publication/title
      [:? :publication/address]
      [:? :publication/archive-url]
      [:? :publication/description]
      [:? :publication/hide-form-title]
      [:? :publication/form-placeholder]
      [:? :publication/form-style]
      [:? :publication/intro]
      [:? :publication/site-url]
      [:? :publication/email-style]
      [:? :publication/banner-image-url]
      [:? :publication/default-author-name]
      [:? :publication/default-author-url]
      [:? :publication/default-author-image-url]
      :publication/padding-color
      :publication/background-color
      :publication/text-color
      :publication/primary-color]}
    {:request/tab settings-tab-query}]]

  (fn [{:biff.datastar/keys [tab-id]
        :biff.fx/keys       [random-uuid7-seq]} result]
    (let [publication (:request/publication result)
          kind        (:request/preview-kind result)
          request-id  (first random-uuid7-seq)]
      (if (and publication tab-id (#{"form" "one" "multi"} kind))
        {:result     result
         :tab-id     tab-id
         :kind       kind
         :request-id request-id

         :_clear
         [:biff.sqlite.fx/execute
          (tab/write-statement
           tab-id
           (:request/tab result)
           {:tab/settings-preview
            {:publication/id      (:publication/id publication)
             :settings/kind       kind
             :settings/open       true
             :settings/request-id request-id
             :settings/values     (:request/publication-settings result)}})]}
        {:biff.fx/return {:status 404}})))

  (fn [_ {:keys [result] :as state}]
    (assoc state :publication
           [:biff.graph.fx/query
            {:publication/id (get-in result [:request/publication
                                             :publication/id])}
            [:publication/id
             :publication/title
             [:? :publication/address]
             [:? :publication/archive-url]
             [:? :publication/description]
             [:? :publication/hide-form-title]
             [:? :publication/form-placeholder]
             [:? :publication/form-style]
             [:? :publication/intro]
             [:? :publication/site-url]
             [:? :publication/email-style]
             [:? :publication/banner-image-url]
             [:? :publication/default-author-name]
             [:? :publication/default-author-url]
             [:? :publication/default-author-image-url]
             :publication/padding-color
             :publication/background-color
             :publication/text-color
             :publication/primary-color]]))

  (fn [{:biff.fx/keys [now random-uuid7-seq]}
       {:keys [result publication kind] :as state}]
    (let [values      (reduce
                       (fn [values field]
                         (if (str/blank? (get values field))
                           (dissoc values field)
                           values))
                       (:request/publication-settings result)
                       [:publication/banner-image-url
                        :publication/default-author-image-url])
          publication (merge publication values
                             (pending-images publication
                                             (:request/tab result)))]
      (assoc state :content
             (if (= kind "form")
               [:biff.graph.fx/query publication
                [:publication/ui-subscribe-form]]
               [:biff.graph.fx/query
                {:send/publication publication
                 :send/posts       (preview-posts kind now
                                                  (rest random-uuid7-seq))}
                [:send/html]]))))

  (fn [request {:keys [kind content] :as state}]
    (assoc state
           :html (if (= kind "form")
                   (:body
                    (ui/page request
                             ((:publication/ui-subscribe-form content)
                              {:preview true})))
                   (:send/html content))
           :latest-tab
           [:biff.sqlite.fx/execute
            {:select [:tab-state/data]
             :from   :tab-state
             :where  [:= :tab-state/id (:tab-id state)]}]))

  (fn [_ {:keys [tab-id request-id html latest-tab]}]
    (let [current (or (:tab-state/data (first latest-tab)) {})
          preview (:tab/settings-preview current)]
      (if (and (= request-id (:settings/request-id preview))
               (:settings/open preview))
        {:_write
         [:biff.sqlite.fx/execute
          (tab/write-statement
           tab-id current
           {:tab/settings-preview (assoc preview :settings/html html)})]

         :status 204}
        {:status 204}))))

(defpipeline close-settings-preview
  [:biff.graph.fx/query
   [{[:? :request/publication] [:publication/id]}
    {:request/tab settings-tab-query}]]

  (fn [{:biff.datastar/keys [tab-id]} result]
    (let [publication-id (get-in result [:request/publication
                                         :publication/id])
          preview        (get-in result [:request/tab
                                         :tab/settings-preview])]
      (if (and tab-id publication-id
               (= publication-id (:publication/id preview)))
        {:_write
         [:biff.sqlite.fx/execute
          (tab/write-statement
           tab-id (:request/tab result)
           {:tab/settings-preview (assoc preview :settings/open false)})]

         :status 204}
        {:status 404}))))

(defpipeline save-settings
  [:biff.graph.fx/query
   [{[:? :request/publication]
     [:publication/id
      [:? :publication/address]
      [:? :publication/automatic-send-threshold]
      {:publication/feed [:feed/url]}]}
    {:request/feed [:feed/url]}
    {:request/publication-settings
     [:publication/title
      [:? :publication/address]
      [:? :publication/archive-url]
      [:? :publication/description]
      [:? :publication/hide-form-title]
      [:? :publication/form-placeholder]
      [:? :publication/form-style]
      [:? :publication/intro]
      [:? :publication/site-url]
      [:? :publication/email-style]
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
      :publication/require-confirmation]}
    {:request/tab
     [{[:? :tab/publication-images]
       [:publication/id
        [:? :publication/banner-image-url]
        [:? :publication/default-author-image-url]]}]}]]

  (fn [{:biff.fx/keys [now]} result]
    (if-let [publication (:request/publication result)]
      (let [settings (merge (:request/publication-settings result)
                            (pending-images publication
                                            (:request/tab result)))

            old-auto (some? (:publication/automatic-send-threshold publication))

            auto (:publication/automatic-sending settings)

            set-values
            (merge
             (into {}
                   (map (fn [[field _ _]]
                          [field (setting-value field (get settings field))])
                        setting-fields))
             (select-keys settings
                          [:publication/banner-image-url
                           :publication/default-author-image-url])
             {:publication/form-style
              (:publication/form-style settings)}
             {:publication/email-style
              (:publication/email-style settings)

              :publication/hide-form-title
              (boolean (:publication/hide-form-title settings))}
             {:publication/require-confirmation
              (boolean (:publication/require-confirmation settings)),

              :publication/welcome-html
              (or (:publication/welcome-html settings) ""),

              :publication/automatic-send-threshold
              (cond
                (not auto) nil
                (not old-auto) now

                :else (:publication/automatic-send-threshold publication))})

            sql-set-values
            (-> set-values
                (update :publication/form-style #(when % [:lift %]))
                (update :publication/email-style #(when % [:lift %])))

            feed-url
            (str/trim (or (get-in result [:request/feed :feed/url]) ""))

            old-feed-url (get-in publication [:publication/feed :feed/url])]
        {:publication  (assoc publication :pending-settings set-values)
         :set-values   set-values
         :feed-url     feed-url
         :feed-changed (not= feed-url old-feed-url)

         :_write
         [:biff.sqlite.fx/authorized-write
          {:update :publication,
           :set    sql-set-values,
           :where  [:= :publication/id (:publication/id publication)]}]})
      {:biff.fx/return {:status 404}}))

  (fn [_ctx {:keys [feed-url feed-changed publication]}]
    (if feed-changed
      (let [threshold
            (get (:pending-settings publication)
                 :publication/automatic-send-threshold)]
        {:url  feed-url
         :data {:publication (assoc publication
                                    :publication/automatic-send-threshold
                                    threshold)
                :defer-write true
                :force-fetch true}})
      {:biff.fx/return {:status 204}}))

  feed/load-existing

  feed/fetch

  feed/load-canonical

  feed/load-posts

  feed/store-content

  feed/persist

  feed/finish

  (fn [{:biff.fx/keys [now]}
       {:keys [data] feed-record :feed :as sync}]
    (let [publication (:publication data)]
      (if (and (:success sync) (seq (:post-ids sync)))
        {:_write
         [:biff.sqlite.fx/authorized-write-tx
          (into (:write-statements sync)
                [{:update :publication
                  :set    (cond->
                           {:publication/feed-id
                            (:feed/id feed-record)

                            :publication/feed-id-updated-at now}
                            (:publication/automatic-send-threshold publication)
                            (assoc :publication/automatic-send-threshold now))
                  :where  [:= :publication/id (:publication/id publication)]}
                 {:update :post
                  :set    {:post/present-as-of now}
                  :where  [:in :post/id (:post-ids sync)]}])]

         :biff.fx/return {:status 204}}
        {:biff.fx/return {:status 422
                          :body   "The feed has no usable posts."}}))))

(def image-fields
  {"banner" :publication/banner-image-url
   "author" :publication/default-author-image-url})

(defpipeline upload-image
  [:biff.graph.fx/query
   [{[:? :request/publication] [:publication/id]}
    {:request/tab
     [{[:? :tab/publication-images]
       [:publication/id
        [:? :publication/banner-image-url]
        [:? :publication/default-author-image-url]]}]}]]

  (fn [{:keys         [path-params]
        :biff.fx/keys [random-uuid7-seq]
        :as           ctx}
       result]
    (let [tab-id (or (:biff.datastar/tab-id ctx)
                     (request/uuid
                      (request/value ctx :biff.datastar/client-tab-id)))
          field  (get image-fields (:field path-params))
          upload (request/value ctx :request/image)
          type   (:content-type upload)
          ext    (get {"image/png"  ".png"
                       "image/jpeg" ".jpg"
                       "image/gif"  ".gif"
                       "image/webp" ".webp"}
                      type)]
      (if (and (:request/publication result)
               tab-id
               field
               (map? upload)
               (:tempfile upload)
               ext)
        (let [object-key (str (first random-uuid7-seq) ext)]
          {:publication (:request/publication result)
           :tab-state   (:request/tab result)
           :tab-id      tab-id
           :field       field
           :object-key  object-key
           :_upload     [:platypub.fx/put-object
                         {:key          object-key
                          :value        (:tempfile upload)
                          :content-type type
                          :headers      {"x-amz-acl" "public-read"}}]})
        {:biff.fx/return {:status 422}})))

  (fn [{:keys [platypub/cdn-url-template]}
       {:keys [publication field object-key tab-id tab-state]}]
    {:_write
     [:biff.sqlite.fx/execute
      (tab/write-statement
       tab-id
       tab-state
       {:tab/publication-images
        (assoc (or (pending-images publication tab-state)
                   {:publication/id (:publication/id publication)})
               field
               (format cdn-url-template object-key))})]

     :biff.fx/return
     {:status 204}}))

(def module
  {:biff.ring/routes
   [["" {:middleware [mid/wrap-app-access]}
     [(routes/publication-settings) {:get settings-page, :post save-settings}]
     [(preview-path) {:post settings-preview}]
     [(close-preview-path) {:post close-settings-preview}]
     [(image-path) {:post upload-image}]]]})
