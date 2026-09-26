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
   [{:request/publication
     [:publication/id
      :publication/title
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
          {:class ["mx-auto w-full max-w-5xl p-6"]}
          [:a
           {:href  (routes/publication (:publication/id
                                        publication)),
            :class ["text-blue-700"]} "← Publication"]
          [:h1
           {:class ["my-4 text-3xl font-bold"]}
           "Publication settings"]
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
            [:button
             {:class ["rounded bg-blue-600 px-4 py-2 text-white"]}
             "Save settings"]]
           [:aside
            {:class ["rounded border p-6"]}
            [:h2
             {:class ["text-xl font-semibold"]}
             "Previews"]
            [:h3 {:class ["mt-4 font-semibold"]} "Subscribe form"]
            [:div
             {:style (str "background:"
                          (:publication/background-color
                           publication)
                          ";color:" (:publication/text-color
                                     publication)),
              :class ["mt-4 p-6"]}
             (when-let [image (:publication/banner-image-url
                               publication)]
               [:img
                {:src image, :class ["mb-4 max-w-full"]}])
             [:h3
              {:class ["text-2xl font-bold"]}
              (:publication/title publication)]
             [:p (:publication/intro publication)]
             [:div {:class ["mt-3 flex gap-2"]}
              [:input {:type        "email"
                       :placeholder "you@example.com"
                       :class       ["min-w-0 flex-1 rounded border p-2"]}]
              [:button
               {:style (str "background:"
                            (:publication/primary-color publication))
                :class ["rounded px-3 py-2 text-white"]}
               "Subscribe"]]]
            [:h3 {:class ["mt-6 font-semibold"]} "Email"]
            [:div
             {:style (str "background:"
                          (:publication/background-color publication)
                          ";color:" (:publication/text-color publication))
              :class ["mt-2 p-6"]}
             [:h3 {:class ["text-2xl font-bold"]} "Lorem ipsum"]
             [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit."]
             [:a
              {:href  "#"
               :style (str "color:"
                           (:publication/primary-color publication))}
              "Read online"]]]]]))
      {:status 404})))

(defpipeline save-settings
  [:biff.graph.fx/query
   [{:request/publication
     [:publication/id
      [:? :publication/automatic-send-threshold]
      {:publication/feed [:feed/url]}]}
    {:request/feed [:feed/url]}
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
                          [field
                           (not-empty
                            (str/trim
                             (or (get settings field) "")))])
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
   [{:request/publication [:publication/id]}
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
