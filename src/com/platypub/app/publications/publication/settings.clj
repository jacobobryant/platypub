(ns com.platypub.app.publications.publication.settings
  (:require [clojure.string :as str]
            [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.middleware :as mid]
            [com.platypub.lib.feed :as feed]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]))

(defpath root-path "")

(def setting-fields
  [[:publication/title "Title" "text"]
   [:publication/description "Description" "text"]
   [:publication/intro "Intro" "text"]
   [:publication/banner-image-url "Banner image URL" "url"]
   [:publication/default-author-name
    "Default author name"
    "text"]
   [:publication/default-author-url
    "Default author URL"
    "url"]
   [:publication/default-author-image-url
    "Default author image URL"
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
   (select-keys publication (map first setting-fields))
   {:request/feed-url feed-url

    :publication/automatic-sending
    (some? (:publication/automatic-send-threshold publication))

    :publication/require-confirmation
    (:publication/require-confirmation publication)}))

(defpipeline settings-page
  [:biff.graph.fx/query
   [{:request/publication
     [:publication/id
      :publication/title
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
      :publication/welcome-html
      :publication/automatic-send-threshold
      :publication/require-confirmation
      {:publication/feed [:feed/url]}]}]]

  (fn [request result]
    (if-let [publication (:request/publication result)]
      (let [feed-url (get-in publication [:publication/feed :feed/url])]
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
             "Preview"]
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
             [:p
              "Lorem ipsum dolor sit amet, consectetur adipiscing elit."]]]]]))
      {:status 404})))

(defpipeline save-settings
  [:biff.graph.fx/query
   [{:request/publication
     [:publication/id
      :publication/automatic-send-threshold
      {:publication/feed [:feed/url]}]}
    {:request/feed [:feed/url]}
    {:request/publication-settings
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
      :publication/welcome-html
      :publication/automatic-sending
      :publication/require-confirmation]}]]

  (fn [{:biff.fx/keys [now]} result]
    (if-let [publication (:request/publication result)]
      (let [settings (:request/publication-settings result)

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
        {:publication  publication
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
      {:url  feed-url
       :data {:publication publication, :defer-write true}}
      {:biff.fx/return {:status 204}}))

  feed/load-existing

  feed/fetch

  feed/load-canonical

  feed/load-posts

  feed/persist

  feed/finish

  (fn [{:biff.fx/keys [now]} {:keys [data] :as feed}]
    (let [publication (:publication data)]
      {:_write
       [:biff.sqlite.fx/authorized-write-tx
        (into (:write-statements feed)
              [{:update :publication
                :set    (cond->
                         {:publication/feed-id            (:feed/id feed)
                          :publication/feed-id-updated-at now}
                          (:publication/automatic-send-threshold publication)
                          (assoc :publication/automatic-send-threshold now))
                :where  [:= :publication/id (:publication/id publication)]}
               {:update :post
                :set    {:post/present-as-of now}
                :where  [:in :post/id (:post-ids feed)]}])]

       :biff.fx/return {:status 204}})))

(def module
  {:biff.ring/routes
   [[(root-path)
     {:middleware [mid/wrap-app-access]}
     [(routes/publication-settings)
      {:get settings-page, :post save-settings}]]]})
