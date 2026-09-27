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

(defpath root-path "")
(defpath image-path "/app/publications/:publication-id/settings/image/:field")

(def setting-fields
  [[:publication/title "Title" "text"]
   [:publication/address "Address" "text"]
   [:publication/description "Description" "text"]
   [:publication/intro "Intro" "text"]
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
   {:request/feed-url         feed-url
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

(defpipeline settings-page
  [:biff.graph.fx/query
   [{[:? :request/publication]
     [:publication/id
      :publication/title
      [:? :publication/address]
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
      [:? :publication/automatic-send-threshold]
      :publication/require-confirmation
      {:publication/feed [:feed/url]}]}
    {:request/tab
     [{[:? :tab/publication-images]
       [:publication/id
        [:? :publication/banner-image-url]
        [:? :publication/default-author-image-url]]}]}]]

  (fn [request result]
    (if-let [publication (:request/publication result)]
      (let [publication (merge publication
                               (pending-images publication
                                               (:request/tab result)))
            feed-url    (get-in publication [:publication/feed :feed/url])]
        (ui/app-shell
         request
         [:main
          {:data-signals__ifmissing
           (datastar/signals-json {:settings/activedialog false})

           :class ["mx-auto w-full max-w-5xl p-6"]}
          (ui/publication-header publication :settings)
          [:div
           {:class ["mb-6 grid gap-4 sm:grid-cols-2"]}
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
                       :required true}]])]
          [:div
           {:class ["grid gap-8 lg:grid-cols-2"]}
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
               :type      "url",
               :required  true,

               :class ["mt-1 block w-full rounded border p-2"]}]]
            (for [[field label input-type] setting-fields]
              [:label
               label
               [:input
                {:data-bind (datastar/signal-name field),
                 :type      input-type,

                 :class ["mt-1 block w-full rounded border p-2"]}]])
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

               :class ["mt-1 h-32 w-full rounded border p-2"]}]]
            [:div {:class ["flex flex-wrap items-center gap-4"]}
             [:button
              {:type "button"

               :data-on:click
               "$settings_activedialog = 'subscribe-preview'"

               :class ["text-primary hover:underline"]}
              "Preview subscribe form"]
             [:button
              {:type "button"

               :data-on:click
               "$settings_activedialog = 'email-preview'"

               :class ["text-primary hover:underline"]}
              "Preview email"]]
            [:div
             [:button {:class ["rounded bg-primary px-4 py-2 text-white"]}
              "Save settings"]]]
           [:div
            [:section {:class ["rounded border border-border bg-surface p-5"]}
             [:h2 {:class ["text-xl font-semibold"]}
              "Archive publication"]
             [:p {:class ["my-4"]}
              (str "If you archive this publication, the subscribe forms "
                   "will be disabled and no emails will be sent to existing "
                   "subscribers.")]
             [:button
              {:type "button"

               :data-on:click
               "$settings_activedialog = 'archive-publication'"

               :class ["rounded border border-border px-4 py-2"]}
              "Archive publication"]]
            (ui/modal
             {:id "subscribe-preview"
              :class ["w-full max-w-2xl rounded border border-border p-0"
                      "bg-surface shadow-xl"]}
             "$settings_activedialog === 'subscribe-preview'"
             "$settings_activedialog = false"
             [:div
              {:data-attr:style
               (str "'background:' + $publication_padding_color"
                    " + ';color:' + $publication_text_color")

               :class ["p-8"]}
              [:img
               {:data-attr:src "$publication_banner_image_url"
                :data-show     "$publication_banner_image_url"
                :alt           ""
                :class         ["mb-4 max-w-full"]}]
              [:h3 {:data-text "'Subscribe to ' + $publication_title"
                    :class     ["text-2xl font-bold"]}]
              [:p {:data-text "$publication_description"}]
              [:div {:class ["mt-3 flex gap-2"]}
               [:input {:type        "email"
                        :placeholder "you@example.com"
                        :class       ["min-w-0 flex-1 rounded border p-2"]}]
               [:button
                {:type            "button"
                 :data-attr:style "'background:' + $publication_primary_color"
                 :class           ["rounded px-3 py-2 text-white"]}
                "Subscribe"]]
              [:button {:type "button"

                        :data-on:click
                        "$settings_activedialog = false"

                        :class ["mt-5 text-primary"]} "Close"]])
            (ui/modal
             {:id "email-preview"
              :class ["w-full max-w-2xl rounded border border-border p-0"
                      "bg-surface shadow-xl"]}
             "$settings_activedialog === 'email-preview'"
             "$settings_activedialog = false"
             [:div
              {:data-attr:style
               (str "'background:' + $publication_background_color"
                    " + ';color:' + $publication_text_color")

               :class ["p-8"]}
              [:h3 {:class ["text-2xl font-bold"]} "Lorem ipsum"]
              [:p {:data-text "$publication_intro" :class ["italic"]}]
              [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit."]
              [:a {:href            "#"
                   :data-attr:style "'color:' + $publication_primary_color"}
               "Read online"]
              [:button {:type "button"

                        :data-on:click
                        "$settings_activedialog = false"

                        :class ["mt-5 block text-primary"]} "Close"]])
            (ui/modal
             {:id "archive-publication"
              :class ["w-full max-w-md rounded border border-border bg-surface"
                      "p-6 shadow-xl"]}
             "$settings_activedialog === 'archive-publication'"
             "$settings_activedialog = false"
             [:h2 {:class ["text-xl font-semibold"]} "Archive publication?"]
             [:p {:class ["my-4"]}
              (str "Subscribers will no longer be able to subscribe and "
                   "sending will stop.")]
             [:div {:class ["flex justify-end gap-2"]}
              [:button {:type "button"

                        :data-on:click
                        "$settings_activedialog = false"

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
             {:publication/require-confirmation
              (boolean (:publication/require-confirmation settings)),

              :publication/welcome-html
              (or (:publication/welcome-html settings) ""),

              :publication/automatic-send-threshold
              (cond
                (not auto) nil
                (not old-auto) now

                :else (:publication/automatic-send-threshold publication))})

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
           :set    set-values,
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
                         object-key
                         (:tempfile upload)
                         type]})
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
   [[(root-path)
     {:middleware [mid/wrap-app-access]}
     [(routes/publication-settings)
      {:get settings-page, :post save-settings}]
     [(image-path) {:post upload-image}]]]})
