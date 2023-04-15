(ns com.platypub.sites
  (:require [com.biffweb :as biff :refer [q]]
            [com.platypub.middleware :as mid]
            [com.platypub.netlify :as netlify]
            [com.platypub.ui :as ui]
            [com.platypub.util :as util]
            [clj-http.client :as http]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [xtdb.api :as xt]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ring.util.io :as ring-io]
            [ring.util.mime-type :as mime]
            [lambdaisland.uri :as uri]
            [babashka.fs :as fs])
  (:import [org.jsoup Jsoup]))

(defn edit-site [{:keys [site params] :as sys}]
  (biff/submit-tx sys
    [(into
      {:db/doc-type :site
       :xt/id (:xt/id site)
       :db/op :update
       :site/title (:title params)
       :site/url (:url params)
       :site/theme (:theme params)}
      (util/params->custom-fields sys))])
  {:status 303
   :headers {"location" (util/make-url "sites" (:xt/id site))}})

(defn new-site [{:keys [session] :as req}]
  (let [id (random-uuid)
        {:keys [ssl_url
                name
                site_id]} (:body (netlify/create! req))]
    (biff/submit-tx req
      [{:db/doc-type :site
        :xt/id id
        :site/user (:uid session)
        :site/url ssl_url
        :site/title name
        :site/theme "default"
        :site/netlify-id site_id}])
    {:status 303
     :headers {"location" (str "/sites/" id)}}))

(defn delete-site [{:keys [path-params biff/db site] :as req}]
  (netlify/delete! req {:site-id (:site/netlify-id site)})
  (biff/submit-tx req
    [{:xt/id (:xt/id site)
      :db/op :delete}])
  {:status 303
   :headers {"location" "/sites"}})

(defn export [sys]
  {:status 200
   :headers {"content-type" "application/edn"
             "content-disposition" "attachment; filename=\"input.edn\""}
   :body (pr-str (util/get-render-opts sys))})

(defn generate! [{:keys [com.platypub/code-last-modified biff/db dir site params] :as sys}]
  (let [render-opts (util/get-render-opts sys)
        theme (:site/theme site)
        theme' (util/resolve-theme sys (:site/theme site))
        _hash (str (hash [render-opts @code-last-modified]))]
    (when (or (:force params)
              (not= (biff/catchall (slurp (io/file dir "_hash"))) _hash))
      (fs/delete-tree dir)
      ((:render-site theme') (assoc render-opts :dir dir))
      (spit (io/file dir "_hash") _hash))))

(defn update-urls [html f]
  (let [doc (Jsoup/parse html)]
    (doseq [attr ["href" "src"]
            element (.select doc (str "[" attr "]"))]
      (.attr element attr (f (.attr element attr))))
    (.outerHtml doc)))

(defn preview [{:keys [biff/db path-params params site] :as sys}]
  (let [dir (io/file "storage/previews" (str (:xt/id site)))
        _ (generate! (assoc sys :dir dir))
        path (or (:path params) "/")
        file (->> (file-seq (io/file dir "public"))
                  (filter (fn [file]
                            (and (.isFile file)
                                 (= (str/replace (subs (.getPath file) (count (str dir "/public")))
                                                 #"/index.html$"
                                                 "/")
                                    path))))
                  first)
        rewrite-url (fn [href]
                      (let [url (uri/join (:site/url site) path href)]
                        (if (str/starts-with? (str url) (:site/url site))
                          (str "/sites/" (:xt/id site) "/preview?"
                               (uri/map->query-string {:path (:path url)}))
                          href)))
        [mime body] (cond
                      (some-> file (.getPath) (str/ends-with? ".html"))
                      ["text/html"
                       (update-urls (slurp file) rewrite-url)]

                      (some-> file (.getPath) (str/ends-with? ".css"))
                      ["text/css"
                       (str/replace (slurp file)
                                    #"url\(([^\)]+)\)"
                                    (fn [[_ href]]
                                      (str "url(" (rewrite-url href) ")")))])]
    (cond
      body {:status 200
            :headers {"content-type" mime}
            :body body}
      file (util/serve-static-file file)
      :else {:status 404
             :headers {"content-type" "text/html"}
             :body "<h1>Not found.</h1>"})))

(defn publish [{:keys [biff/secret biff/db path-params site] :as sys}]
  (let [dir (io/file "storage/deploys" (str (random-uuid)))]
    (generate! (assoc sys :dir dir))
    (netlify/deploy! {:api-key (secret :netlify/api-key)
                      :site-id (:site/netlify-id site)
                      :dir (str dir)})
    (fs/delete-tree dir)
    {:status 303
     :headers {"location" "/sites"}}))

(defn custom-config [{:keys [path-params biff/db user site params] :as sys}]
  (let [{:keys [theme]} params
        site (if (util/resolve-theme sys theme)
               (util/merge-site-config sys (assoc (xt/entity db (:xt/id site))
                                                  :site/theme theme))
               site)
        sys (assoc sys :site site)]
    [:div#custom-config
     (for [k (:site.config/site-fields site)]
       [:<>
        (ui/custom-field sys k)
        [:.h-3]])]))

(defn edit-site-page [{:keys [com.platypub/themes path-params biff/db user site] :as sys}]
  (ui/nav-page
   (merge sys
          {:base/head [[:script (biff/unsafe (slurp (io/resource "darkmode.js")))]]
           :current :sites})
   [:.bg-gray-100.dark:bg-stone-800.dark:text-gray-50.flex-grow
    [:.max-w-screen-sm
     (biff/form
      {:id "edit"
       :action (str "/sites/" (:xt/id site))
       :class '[flex flex-col flex-grow]}
      (ui/text-input {:id "netlify-id" :label "Netlify ID" :value (:site/netlify-id site) :disabled true})
      [:.h-3]
      (ui/text-input {:id "url" :label "URL" :value (:site/url site)})
      [:.h-3]
      (ui/text-input {:id "title" :label "Title" :value (:site/title site)})
      [:.h-3]
      (ui/select {:id "theme"
                  :name "theme"
                  :label "Theme"
                  :value (:site/theme site)
                  :options (for [t themes]
                             {:label (:label @(requiring-resolve t)) :value (str t)})
                  :default (first themes)
                  :hx-trigger "change"
                  :hx-get (str "/sites/" (:xt/id site) "/custom-config")
                  :hx-target "#custom-config"
                  :hx-swap "outerHTML"})
      [:.h-3]
      (custom-config sys)
      [:.h-4]
      [:button.btn.w-full {:type "submit"} "Save"])
     [:.h-3]
     (biff/form
      {:onSubmit "return confirm('Delete site?')"
       :method "POST"
       :action (str "/sites/" (:xt/id site) "/delete")}
      [:button.text-red-600.hover:text-red-700 {:type "submit"} "Delete"])
     [:.h-6]]]))

(defn site-list-item [{:keys [site/title
                              site/url
                              xt/id
                              site.config/items]}]
  [:.flex.items-center.mb-4
   [:div
    [:div [:a.link.text-lg {:href (str "/sites/" id)}
           (or (not-empty (str/trim title)) "[No title]")]
     [:span.md:hidden
      " " biff/emdash " "
      (util/join
       ", "
       (for [{:keys [slug label]} items]
         [:a.link.text-lg {:href (util/make-url "site" id slug)}
          (str label "s")]))]]
    [:.text-sm.text-stone-600.dark:text-stone-300
     [:a.hover:underline {:href url :target "_blank"} "View"]
     ui/interpunct
     [:a.hover:underline {:href (str "/sites/" id "/preview?path=/")
                          :target "_blank"}
      "Preview"]
     ui/interpunct
     (biff/form
      {:method "POST"
       :action (str "/sites/" id "/publish")
       :class "inline-block"}
      [:button.hover:underline {:type "submit"} "Publish"])
     ui/interpunct
     [:a.hover:underline {:href (str "/sites/" id "/export")
                          :target "_blank"}
      "Export"]]]])

(defn sites-page [{:keys [biff/secret sites] :as sys}]
  (ui/nav-page
   (merge sys {:current :sites})
   (biff/form
    {:action "/sites"}
    (when (nil? (secret :netlify/api-key))
      [:p "You need to enter a Netlify API key"])
    [:button.btn {:type "submit"
                  :disabled (nil? (secret :netlify/api-key))} "New site"])
   [:.h-6]
   (->> sites
        (sort-by :site/title)
        (map site-list-item))))

(def plugin
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/sites" {:get sites-page
                       :post new-site}]
            ["/sites/:site-id"
             ["" {:get edit-site-page
                  :post edit-site}]
             ["/custom-config" {:get custom-config}]
             ["/delete" {:post delete-site}]
             ["/publish" {:post publish}]
             ["/preview" {:get preview}]
             ["/export" {:get export}]]]})
