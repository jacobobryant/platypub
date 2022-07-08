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

(defn edit-site [{:keys [biff/db params] :as req}]
  (let [{:keys [id
                url
                title
                description
                image
                tag
                theme
                redirects]} params
        custom-schema (when-some [t (:site/theme (xt/entity db (parse-uuid id)))]
                        (biff/catchall (edn/read-string (slurp (str "themes/" t "/custom-schema.edn")))))
        custom-config (->> custom-schema
                           (map (fn [{k :key}]
                                  ;; If k is namespaced, then in params it'll
                                  ;; be a string instead of a keyword.
                                  [k (or (get params (subs (str k) 1))
                                         (get params k)
                                         "")]))
                           (into {}))]
    (biff/submit-tx req
      [(into {:db/doc-type :site
              :db/op :update
              :xt/id (parse-uuid id)
              :site/title title
              :site/url url
              :site/description description
              :site/image image
              :site/tag tag
              :site/theme theme
              :site/redirects redirects
              :site/custom-config custom-config})])
    {:status 303
     :headers {"location" (str "/sites/" id)}}))

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

(defn export [{:keys [path-params] :as req}]
  {:status 200
   :headers {"content-type" "application/edn"
             "content-disposition" "attachment; filename=\"input.edn\""}
   :body (pr-str (assoc (util/get-render-opts req)
                        :site-id (parse-uuid (:id path-params))))})

(defn generate! [{:keys [biff/db path-params params dir] :as req}]
  (let [render-opts (assoc (util/get-render-opts req)
                           :site-id (parse-uuid (:id path-params)))
        path (str (.getPath (io/file "bin")) ":" (System/getenv "PATH"))
        {:keys [site/theme]} (xt/entity db (:site-id render-opts))
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

(defn preview [{:keys [biff/db path-params params] :as req}]
  (let [site (xt/entity db (parse-uuid (:id path-params)))
        dir (io/file "storage/previews" (:id path-params))
        _ (generate! (assoc req :dir dir))
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
        site (xt/entity db site-id)
        custom-schema (when-some [t (:site/theme site)]
                        (biff/catchall (edn/read-string (slurp (str "themes/" t "/custom-schema.edn")))))]
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
          (ui/text-input {:id "netlify-id" :label "Netlify ID" :value (:site/netlify-id site) :disabled true})
          [:.h-3]
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
          (ui/textarea {:id "redirects" :label "Redirects" :value (:site/redirects site)})
          [:.h-3]
          (ui/text-input {:id "theme" :label "Theme" :value (:site/theme site)})
          (for [{:keys [label description default key type]} custom-schema]
            [:.mt-3
             ((case type
                :textarea ui/textarea
                ui/text-input)
              {:id (subs (str key) 1)
               :label label
               :description description
               :value (get-in site [:site/custom-config key] default)})])
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
     (biff/form
       {:method "POST"
        :action (str "/sites/" id "/publish")
        :class "inline-block"}
       [:button.hover:underline {:type "submit"} "Publish"])
     ui/interpunct
     [:a.hover:underline {:href (str "/sites/" id "/export")
                          :target "_blank"}
      "Export"]]]])

(defn sites-page [{:keys [session biff/db netlify/api-key] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        sites (q db
                 '{:find (pull site [*])
                   :in [user]
                   :where [[site :site/user user]]}
                 (:uid session))]
    (ui/nav-page
      {:current :sites
       :email email}
      (biff/form
        {:action "/sites"}
        (when (nil? api-key)
          [:p "You need to enter a Netlify API key"])
        [:button.btn {:type "submit"
                      :disabled (nil? api-key)} "New site"])
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
             ["/preview" {:get preview}]
             ["/export" {:get export}]]]})
