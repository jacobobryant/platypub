(ns com.platypub.app.publications
  (:require [clojure.string :as str]
            [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.feed :as feed]
            [com.platypub.lib.middleware :as mid]
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

(defpipeline publications-page
  [:biff.graph.fx/query
   [{:request/user
     [:user/id
      {:user/publications
       [:publication/id :publication/title :publication/description]}]}]]

  (fn [request result]
    (let [publications (get-in result [:request/user :user/publications])]
      (ui/app-shell
       request
       [:main
        {:class ["mx-auto w-full max-w-5xl p-6"]}
        [:div
         {:class ["flex items-center justify-between"]}
         [:h1 {:class ["text-3xl font-bold"]} "Publications"]]
        [:form
         {:data-on:submit "@post(el.dataset.action)",
          :data-action    (publications-path),

          :data-signals__ifmissing
          (datastar/signals-json {:request/publication-url ""}),

          :class ["my-8 flex gap-2"]}
         [:input {:data-bind   (datastar/signal-name :request/publication-url),
                  :type        "url"
                  :required    true
                  :placeholder "Website or feed URL"
                  :class       ["min-w-0 flex-1 rounded border p-3"]}]
         [:button
          {:class ["rounded bg-blue-600 px-5 py-3 text-white"]}
          "Add publication"]]
        (if (seq publications)
          [:div
           {:class ["grid gap-4"]}
           (for [publication publications]
             [:a
              {:href  (routes/publication (:publication/id publication))
               :class ["rounded border bg-white p-5 hover:border-blue-500"]}
              [:h2
               {:class ["text-xl font-semibold"]}
               (:publication/title publication)]
              [:p
               {:class ["text-sm text-gray-600"]}
               (:publication/description publication)]])]
          [:p
           {:class ["text-gray-600"]}
           "Add your first publication using its website or feed URL."])]))))

(defpipeline create-publication
  [:biff.graph.fx/query
   [{:request/user [:user/id]}
    {:request/new-publication [:publication/url]}]]

  (concat
   [(fn [_ctx result]
      {:url  (get-in result [:request/new-publication :publication/url])
       :data {:user-id (get-in result [:request/user :user/id])}})

    feed/fetch

    (fn [_ctx {:keys [url response data]}]
      (let [urls (discover-feed-urls url response)]
        (if (= 1 (count urls))
          {:url  (first urls)
           :data (assoc data :defer-write true)}
          {:biff.fx/return {:status 204}})))]

   feed/sync-fns

   [(fn [{:biff.fx/keys [now random-uuid7-seq]}
         {:keys [data] :as sync-result}]
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

         :biff.fx/return {:status 204}}))]))

(def module
  {:biff.ring/routes
   [[(root-path)
     {:middleware [mid/wrap-app-access]}
     [(routes/app) {:get publications-page}]
     [(publications-path) {:post create-publication}]]]})
