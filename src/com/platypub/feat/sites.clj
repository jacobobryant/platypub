(ns com.platypub.feat.sites
  (:require [com.biffweb :as biff :refer [q]]
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

(defn edit-site [{:keys [params] :as req}]
  (let [{:keys [id
                url
                title
                description
                image
                tag
                theme
                redirects]} params]
    (biff/submit-tx req
      [{:db/doc-type :site
        :db/op :update
        :xt/id (parse-uuid id)
        :site/title title
        :site/url url
        :site/description description
        :site/image image
        :site/tag tag
        :site/theme theme
        :site/redirects redirects}])
    {:status 303
     :headers {"location" (str "/sites/" id)}}))

(defn new-site [req]
  (let [id (random-uuid)
        {:keys [ssl_url
                name
                site_id]} (:body (netlify/create! req))]
    (biff/submit-tx req
      [{:db/doc-type :site
        :xt/id id
        :site/url ssl_url
        :site/title name
        :site/description ""
        :site/image ""
        :site/tag ""
        :site/theme "default"
        :site/redirects ""
        :site/netlify-id site_id}])
    {:status 303
     :headers {"location" (str "/sites/" id)}}))

(defn delete-site [{:keys [path-params biff/db] :as req}]
  (netlify/delete! req {:site-id (:site/netlify-id (xt/entity db (parse-uuid (:id path-params))))})
  (biff/submit-tx req
    [{:xt/id (parse-uuid (:id path-params))
      :db/op :delete}])
  {:status 303
   :headers {"location" "/sites"}})

(defn download [uri file]
  (io/make-parents file)
  ;; I'm using http/get instead of io/input-stream to prevent a malicious user
  ;; from passing in a path to a local file. Inspecting uri might be another
  ;; option?
  (with-open [in (:body (http/get uri {:as :stream}))
              out (io/output-stream file)]
    (io/copy in out)))

(defn cache-file! [{:keys [url force]}]
  (let [path (io/file "storage/cache/" (str (java.util.UUID/nameUUIDFromBytes (.getBytes url))))]
    (when (or force (not (.exists path)))
      (log/info "Downloading" url)
      (download url path))
    path))

(defn generate! [{:keys [biff/db path-params params dir] :as req}]
  (let [site (xt/entity db (parse-uuid (:id path-params)))
        posts (if-some [tag (:site/tag site)]
                (q db
                   '{:find (pull post [*])
                     :in [tag]
                     :where [[post :post/tags tag]]}
                   tag)
                (q db
                   '{:find (pull post [*])
                     :where [[post :post/title]]}))
        posts (->> posts
                   (filter #(= :published (:post/status %)))
                   (sort-by :post/published-at #(compare %2 %1)))
        theme-files (->> (file-seq (io/file "themes" (:site/theme site)))
                         (filter #(.isFile %)))
        force-refresh (= "true" (:force-refresh params))]
    (biff/sh "rm" "-rf" (str dir))
    (io/make-parents (io/file dir "_"))
    (spit (io/file dir "_input.edn") (pr-str {:site site :posts posts}))
    (doseq [f theme-files]
      (io/copy f (io/file dir (.getName f))))
    (biff/sh "chmod" "+x" (str (io/file dir "theme")))
    (some-> (biff/sh "./theme" :dir dir) not-empty log/info)
    (some->> (biff/catchall (slurp (io/file dir "_files")))
             str/split-lines
             (run! (fn [line]
                     (let [[path url] (str/split line #"\s+")
                           path (io/file dir (subs path 1))
                           cached-path (cache-file! {:url url :force force-refresh})]
                       (io/make-parents path)
                       ;; TODO prevent malicious user from setting path to
                       ;; outside working directory
                       (io/copy cached-path path)))))
    (->> theme-files
         (map (fn [f]
                (io/file dir (subs (str f) (count (str "themes/" (:site/theme site) "/"))))))
         (concat [(io/file dir "_files")
                  (io/file dir "_redirects")
                  (io/file dir "_input.edn")])
         (run! (fn [f]
                 (io/delete-file f true))))))

(defn preview [{:keys [biff/db path-params params] :as req}]
  (let [site (xt/entity db (parse-uuid (:id path-params)))
        post-last-edited (first (q db
                                   '{:find (max edited-at)
                                     :where [[post :post/edited-at edited-at]]}))
        theme-last-modified (->> (file-seq (io/file "themes" (:site/theme site)))
                                 (filter #(.isFile %))
                                 (map ring-io/last-modified-date)
                                 (apply max-key inst-ms))
        h (hash [site post-last-edited theme-last-modified])
        dir (io/file "storage/previews" (str (:xt/id site)))
        force-refresh (= "true" (:force-refresh params))
        _ (when (or force-refresh
                    (not= (biff/catchall (slurp (io/file dir "_hash"))) (str h)))
            (generate! (assoc req :dir dir))
            (spit (io/file dir "_hash") (str h)))
        path (or (:path params) "/")
        file (when-not (#{"/_redirects"
                          "/_files"} path)
               (->> (file-seq (io/file dir))
                    (filter (fn [file]
                              (and (.isFile file)
                                   (= (str/replace (subs (.getPath file) (count (str dir)))
                                                   #"/index.html$"
                                                   "/")
                                      path))))
                    first))
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
      force-refresh {:status 303
                     :headers {"location" (str "/sites/" (:xt/id site) "/preview?path=/")}}
      body {:status 200
            :headers {"content-type" mime}
            :body body}
      file (util/serve-static-file file)
      :else {:status 404
             :headers {"content-type" "text/html"}
             :body "<h1>Not found.</h1>"})))

(defn publish [{:keys [biff/db path-params] :as req}]
  (let [site (xt/entity db (parse-uuid (:id path-params)))
        dir (io/file "storage/deploys" (str (random-uuid)))]
    (generate! (assoc req :dir dir))
    (netlify/deploy! {:api-key (:netlify/api-key req)
                      :site-id (:site/netlify-id site)
                      :dir (str dir)})
    (biff/sh "rm" "-rf" (str dir))
    {:status 303
     :headers {"location" "/sites"}}))

(defn edit-site-page [{:keys [path-params biff/db session] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        site-id (parse-uuid (:id path-params))
        site (xt/entity db site-id)]
    (ui/nav-page
      {:base/head [[:script (biff/unsafe (slurp (io/resource "darkmode.js")))]]
       :current :sites
       :email email}
      [:.bg-gray-100.dark:bg-stone-800.dark:text-gray-50.flex-grow
       [:.max-w-screen-sm
        (biff/form
          {:id "edit"
           :action (str "/sites/" site-id)
           :hidden {:id site-id}
           :class '[flex flex-col flex-grow]}
          (ui/text-input {:id "url" :label "URL" :value (:site/url site)})
          [:.h-3]
          (ui/text-input {:id "title" :label "Title" :value (:site/title site)})
          [:.h-3]
          (ui/textarea {:id "description" :label "Description" :value (:site/description site)})
          [:.h-3]
          (ui/image {:id "image" :label "Image" :value (:site/image site)})
          [:.h-3]
          (ui/text-input {:id "tag" :label "Tag" :value (:site/tag site)})
          [:.h-3]
          (ui/text-input {:id "theme" :label "Theme" :value (:site/theme site)})
          [:.h-3]
          (ui/textarea {:id "redirects" :label "Redirects" :value (:site/redirects site)})
          [:.h-3]
          (ui/text-input {:id "netlify-id" :label "Netlify ID" :value (:site/netlify-id site) :disabled true})
          [:.h-4]
          [:button.btn.w-full {:type "submit"} "Save"])
        [:.h-3]
        (biff/form
          {:onSubmit "return confirm('Delete site?')"
           :method "POST"
           :action (str "/sites/" (:xt/id site) "/delete")}
          [:button.text-red-600.hover:text-red-700 {:type "submit"} "Delete"])
        [:.h-6]]])))

(defn site-list-item [{:keys [site/title
                              site/image
                              site/url
                              xt/id]}]
  [:.flex.items-center.mb-4
   (when image
     [:img.mr-3.max-h-12.max-w-12 {:src image}])
   [:div
    [:div [:a.link.block.text-lg {:href (str "/sites/" id)}
           (or (not-empty (str/trim title)) "[No title]")]]
    [:.text-sm.text-stone-600.dark:text-stone-300
     [:a.hover:underline {:href url :target "_blank"} "View"]
     ui/interpunct
     [:a.hover:underline {:href (str "/sites/" id "/preview?path=/")
                          :target "_blank"}
      "Preview"]
     ui/interpunct
     [:a.hover:underline {:href (str "/sites/" id "/preview?path=/&force-refresh=true")
                          :target "_blank"}
      "Force refresh"]
     ui/interpunct
     (biff/form
       {:method "POST"
        :action (str "/sites/" id "/publish")
        :class "inline-block"}
       [:button.hover:underline {:type "submit"} "Publish"])]]])

(defn sites-page [{:keys [session biff/db] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        sites (q db
                 '{:find (pull site [*])
                   :where [[site :site/title]]})]
    (ui/nav-page
      {:current :sites
       :email email}
      (biff/form
        {:action "/sites"}
        [:button.btn {:type "submit"} "New site"])
      [:.h-6]
      (->> sites
           (sort-by :site/title)
           (map site-list-item)))))

(def features
  {:routes ["" {:middleware [util/wrap-signed-in]}
            ["/sites" {:get sites-page
                       :post new-site}]
            ["/sites/:id"
             ["" {:get edit-site-page
                  :post edit-site}]
             ["/delete" {:post delete-site}]
             ["/publish" {:post publish}]
             ["/preview" {:get preview}]]]})
