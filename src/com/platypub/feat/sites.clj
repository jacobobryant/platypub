(ns com.platypub.feat.sites
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
            [lambdaisland.uri :as uri]))

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

(defn generate! [{:keys [biff/db path-params params dir site] :as sys}]
  (let [render-opts (util/get-render-opts sys)
        path (str (.getPath (io/file "bin")) ":" (System/getenv "PATH"))
        theme (:site/theme site)
        theme-last-modified (->> (file-seq (io/file "themes" theme))
                                 (filter #(.isFile %))
                                 (map ring-io/last-modified-date)
                                 (apply max-key inst-ms))
        _hash (str (hash [render-opts theme-last-modified]))]
    (when (not= (biff/catchall (slurp (io/file dir "_hash"))) _hash)
      (biff/sh "rm" "-rf" (str dir))
      (io/make-parents dir)
      (biff/sh "cp" "-r" (str (io/file "themes" theme)) (str dir))
      (biff/sh "rm" "-rf" (str dir "/node_modules"))
      (when (.exists (io/file dir "package.json"))
        (biff/sh "npm" "install" :dir (str dir)))
      (spit (io/file dir "input.edn") (pr-str render-opts))
      (biff/sh "chmod" "+x" (str (io/file dir "render-site")))
      (some-> (biff/sh "./render-site" :dir dir :env {"PATH" path}) not-empty log/info)
      (spit (io/file dir "_hash") _hash))))

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
                       (str/replace (slurp file)
                                    #"(href|src)=\"([^\"]+)\""
                                    (fn [[_ attr href]]
                                      (str attr "=\"" (rewrite-url href) "\"")))]

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

(defn publish [{:keys [biff/db path-params site] :as sys}]
  (let [dir (io/file "storage/deploys" (str (random-uuid)))]
    (generate! (assoc sys :dir dir))
    (netlify/deploy! {:api-key (util/get-secret sys :netlify/api-key)
                      :site-id (:site/netlify-id site)
                      :dir (str dir)})
    (biff/sh "rm" "-rf" (str dir))
    {:status 303
     :headers {"location" "/sites"}}))

(defn edit-site-page [{:keys [path-params biff/db user site] :as sys}]
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
      (ui/text-input {:id "theme" :label "Theme" :value (:site/theme site)})
      [:.h-3]
      (for [k (:site.config/site-fields site)]
        (list
         (ui/custom-field sys k)
         [:.h-3]))
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

(defn sites-page [{:keys [sites] :as sys}]
  (ui/nav-page
   (merge sys {:current :sites})
   (biff/form
    {:action "/sites"}
    (when (nil? (util/get-secret sys :netlify/api-key))
      [:p "You need to enter a Netlify API key"])
    [:button.btn {:type "submit"
                  :disabled (nil? (util/get-secret sys :netlify/api-key))} "New site"])
   [:.h-6]
   (->> sites
        (sort-by :site/title)
        (map site-list-item))))

(def features
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/sites" {:get sites-page
                       :post new-site}]
            ["/sites/:site-id"
             ["" {:get edit-site-page
                  :post edit-site}]
             ["/delete" {:post delete-site}]
             ["/publish" {:post publish}]
             ["/preview" {:get preview}]
             ["/export" {:get export}]]]})
