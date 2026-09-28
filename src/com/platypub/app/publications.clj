(ns com.platypub.app.publications
  (:require [clojure.string :as str]
            [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.feed :as feed]
            [com.platypub.lib.middleware :as mid]
            [com.platypub.lib.tab :as tab]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes])
  (:import [java.net URI]))

(defpath root-path "")
(defpath publications-path "/app/publications")

(def defaults
  {:publication/padding-color        "#f7f7f2"
   :publication/background-color     "#ffffff"
   :publication/text-color           "#111827"
   :publication/primary-color        "#2563eb"
   :publication/welcome-html         "Thanks for subscribing."
   :publication/address              ""
   :publication/require-confirmation false})

(defn- discover-feed-urls
  [url response]
  (let [final-url (str (or (:uri response) url))

        content-type (get-in response [:headers "content-type"] "")

        body (:body response)]
    (if (or (str/includes? (str/lower-case content-type) "xml")
            (str/includes? (str/lower-case content-type) "json")
            (re-find #"(?is)^\s*(<\?xml|<rss|<feed|\{)" body))
      [final-url]
      (->>
       (re-seq
        (re-pattern
         (str
          "(?is)<link[^>]+(?:type=['\"](?:application/(?:rss|atom)"
          "\\+xml|application/feed\\+json|application/json)['\"])[^>]*>"))
        body)
       (keep (fn [tag]
               (second (re-find #"(?is)href=['\"]([^'\"]+)" tag))))
       (map #(str (.resolve (URI. final-url) %)))
       distinct
       vec))))

(defn- publication-form
  [tab-state]
  (if-let [{:publication/keys [url feed-urls]}
           (:tab/new-publication tab-state)]
    [:form
     {:data-on:submit "@post(el.dataset.action)"
      :data-indicator "publication_creating"
      :data-action    (publications-path)

      :data-signals__ifmissing
      (datastar/signals-json {:request/publication-url url
                              :request/feed-url        nil})

      :class ["grid gap-5"]}
     [:label "Feed"
      [:select {:data-bind (datastar/signal-name :request/feed-url)
                :required  true
                :class     ["mt-1 w-full rounded border border-border p-3"]}
       [:option {:value ""} "Choose a feed"]
       (for [feed-url feed-urls]
         [:option {:value feed-url} feed-url])]]
     [:div {:class ["flex justify-end gap-2"]}
      [:button {:type "button"

                :data-on:click
                "$publication_dialogopen = false"

                :class ["rounded border border-border px-5 py-3"]}
       "Cancel"]
      [:button {:class              ["rounded bg-primary px-5 py-3 text-white"
                                     "disabled:opacity-60"]
                :data-attr:disabled "$publication_creating"}
       [:span {:data-show "!$publication_creating"} "Use this feed"]
       [:span {:data-show "$publication_creating" :style "display:none"}
        "Creating…"]]]]
    [:form
     {:data-on:submit "@post(el.dataset.action)"
      :data-indicator "publication_creating"
      :data-action    (publications-path)

      :data-signals__ifmissing
      (datastar/signals-json {:request/publication-url ""
                              :request/feed-url        nil})

      :class ["grid gap-5"]}
     [:label "Website or feed URL"
      [:input {:data-bind   (datastar/signal-name :request/publication-url)
               :type        "url"
               :required    true
               :placeholder "Website or feed URL"
               :class       ["mt-1 w-full rounded border border-border p-3"]}]]
     [:div {:class ["flex justify-end gap-2"]}
      [:button {:type "button"

                :data-on:click
                "$publication_dialogopen = false"

                :class ["rounded border border-border px-5 py-3"]}
       "Cancel"]
      [:button {:class              ["rounded bg-primary px-5 py-3 text-white"
                                     "disabled:opacity-60"]
                :data-attr:disabled "$publication_creating"}
       [:span {:data-show "!$publication_creating"} "Save"]
       [:span {:data-show "$publication_creating" :style "display:none"}
        "Creating…"]]]]))

(defpipeline publications-page
  [:biff.graph.fx/query
   [{:request/user
     [:user/id
      {:user/active-publications
       [:publication/id
        :publication/title
        [:? :publication/description]]}
      {:user/archived-publications [:publication/id]}]}
    {:request/tab
     [{[:? :tab/new-publication]
       [:publication/url :publication/feed-urls]}]}]]

  (fn [request result]
    (let [publications (get-in result
                               [:request/user :user/active-publications])
          archived     (get-in result
                               [:request/user :user/archived-publications])
          tab-state    (:request/tab result)]
      (ui/app-shell
       request
       [:main
        {:data-signals__ifmissing
         (datastar/signals-json
          {:publication/dialogopen (boolean (:tab/new-publication tab-state))})

         :class ["mx-auto w-full max-w-5xl p-6 lg:p-10"]}
        [:div
         {:class ["flex items-center justify-between"]}
         [:h1 {:class ["text-3xl font-bold"]} "Publications"]
         [:div {:class ["flex items-center gap-4"]}
          (when (seq archived)
            [:a {:href  (routes/archived-publications)
                 :class ["text-primary hover:underline"]}
             "Archived publications"])
          [:button
           {:type "button"

            :data-on:click "$publication_dialogopen = true"

            :class ["rounded bg-primary px-4 py-2 text-white"]}
           "Add publication"]]]
        (ui/modal
         {:id    "add-publication"
          :class ["w-full max-w-xl rounded border border-border"
                  "bg-surface p-0 shadow-xl"]}
         "$publication_dialogopen"
         "$publication_dialogopen = false"
         [:div {:class ["border-b border-border p-5 text-xl font-semibold"]}
          "Add publication"]
         [:div {:class ["p-5"]} (publication-form tab-state)])
        (if (seq publications)
          [:div
           {:class ["mt-8 grid gap-4"]}
           (for [publication publications]
             [:a
              {:href  (routes/publication (:publication/id publication))
               :class ["rounded border border-border bg-surface p-5"
                       "hover:border-primary"]}
              [:h2
               {:class ["text-xl font-semibold"]}
               (:publication/title publication)]
              [:p
               {:class ["text-sm text-muted"]}
               (:publication/description publication)]])]
          [:p
           {:class ["mt-8 text-muted"]}
           "Add your first publication using its website or feed URL."])]))))

(defpipeline create-publication
  [:biff.graph.fx/query
   [{:request/user [:user/id]}
    {:request/new-publication
     [:publication/url [:? :publication/feed-url]]}
    {:request/tab
     [{[:? :tab/new-publication]
       [:publication/url :publication/feed-urls]}]}]]

  (concat
   [(fn [{:keys [biff.datastar/tab-id]} result]
      {:url  (get-in result [:request/new-publication :publication/url])
       :data {:user-id     (get-in result [:request/user :user/id])
              :feed-choice (get-in result
                                   [:request/new-publication
                                    :publication/feed-url])
              :tab-id      tab-id
              :tab         (:request/tab result)}})

    feed/fetch

    (fn [_ctx {:keys [url response data]}]
      (let [urls     (discover-feed-urls url response)
            selected (when (some #{(:feed-choice data)} urls)
                       (:feed-choice data))]
        (cond
          (or selected (= 1 (count urls)))
          {:url    (or selected (first urls))
           :data   (assoc data :defer-write true :force-fetch true)
           :_clear (when (:tab-id data)
                     [:biff.sqlite.fx/execute
                      (tab/write-statement (:tab-id data)
                                           (:tab data)
                                           {:tab/new-publication nil})])}

          (empty? urls)
          {:biff.fx/return {:status 422
                            :body   "No feed was discovered at that URL."}}

          :else
          {:_write         [:biff.sqlite.fx/execute
                            (tab/write-statement
                             (:tab-id data)
                             (:tab data)
                             {:tab/new-publication
                              {:publication/url       url
                               :publication/feed-urls urls}})]
           :biff.fx/return {:status 204}})))]

   feed/sync-fns

   [(fn [{:biff.fx/keys [now random-uuid7-seq]}
         {:keys [data] :as sync-result}]
      (if-not (and (:success sync-result) (seq (:post-ids sync-result)))
        {:biff.fx/return {:status 422
                          :body   "The feed has no usable posts."}}
        (let [publication-id (first random-uuid7-seq)
              user-id        (:user-id data)
              feed           (:feed sync-result)
              author         (:author feed)

              row
              (merge
               defaults
               {:publication/id                       publication-id
                :publication/created-at               now
                :publication/user-id                  user-id
                :publication/feed-id                  (:feed/id feed)
                :publication/feed-id-updated-at       now
                :publication/automatic-send-threshold now

                :publication/title (or (:title feed) (:feed/url feed))}
               (when-let [value (:description feed)]
                 {:publication/description value
                  :publication/intro       value})
               (when-let [value (:name author)]
                 {:publication/default-author-name value
                  :publication/default-author-url  (:url author)

                  :publication/default-author-image-url (:image author)}))]
          {:_write
           [:biff.sqlite.fx/authorized-write-tx
            (into (:write-statements sync-result)
                  [{:insert-into :publication, :values [row]}
                   {:update :post,
                    :set    {:post/present-as-of now},
                    :where  [:in :post/id (:post-ids sync-result)]}])]

           :biff.fx/return {:status 204}})))]))

(def module
  {:biff.ring/routes
   [[(root-path)
     {:middleware [mid/wrap-app-access]}
     [(routes/app) {:get publications-page}]
     [(publications-path) {:post create-publication}]]]})
