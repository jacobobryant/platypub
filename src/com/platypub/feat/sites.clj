(ns com.platypub.feat.sites
  (:require [com.biffweb :as biff :refer [q]]
            [com.platypub.netlify :as netlify]
            [com.platypub.ui :as ui]
            [com.platypub.util :as util]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [xtdb.api :as xt]
            [ring.middleware.anti-forgery :as anti-forgery]
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
        :site/theme (symbol theme)
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
        :site/theme 'com.platypub.themes.default/theme
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

(defn publish [{:keys [biff/db path-params] :as req}]
  (let [site (xt/entity db (parse-uuid (:id path-params)))
        posts (if-some [tag (:site/tag site)]
                (q db
                   '{:find (pull post [*])
                     :in [tag]
                     :where [[post :post/tag tag]]}
                   tag)
                (q db
                   '{:find (pull post [*])
                     :where [[post :post/title]]}))
        posts (->> posts
                   (filter #(= :published (:post/status %)))
                   (sort-by :post/published-at #(compare %2 %1)))
        theme (:site @(requiring-resolve (:site/theme site)))
        dir (str "storage/site/" (random-uuid))]
    (doseq [[path contents] (theme req {:site site
                                        :posts posts})
            :let [file (io/file (str dir path))]]
      (io/make-parents file)
      (spit file contents))
    (netlify/deploy! {:api-key (:netlify/api-key req)
                      :site-id (:site/netlify-id site)
                      :dir dir})
    {:status 303
     :headers {"location" "/sites"}}))

(defn preview [{:keys [biff/db path-params params] :as req}]
  (let [site (xt/entity db (parse-uuid (:id path-params)))
        posts (if-some [tag (:site/tag site)]
                (q db
                   '{:find (pull post [*])
                     :in [tag]
                     :where [[post :post/tag tag]]}
                   tag)
                (q db
                   '{:find (pull post [*])
                     :where [[post :post/title]]}))
        posts (->> posts
                   (filter #(= :published (:post/status %)))
                   (sort-by :post/published-at #(compare %2 %1)))
        theme (:site @(requiring-resolve (:site/theme site)))
        files (theme req {:site site :posts posts})
        path (or (:path params) "/index.html")
        path (if (contains? files path)
               path
               (str/replace-first path #"/?$" "/index.html"))
        body (get files path)
        body (some-> body
                     (str/replace
                       #"href=\"([^\"]+)\""
                       (fn [[_ href]]
                         (str "href=\""
                              (if (some #(str/starts-with? href %) ["/" (:site/url site)])
                                (str "/sites/" (:xt/id site) "/preview?"
                                     (uri/map->query-string {:path (:path (uri/uri href))}))
                                href)
                              "\""))))
        mime-type (some-> (re-find #"\.([^./\\]+)$" path)
                          second
                          str/lower-case
                          mime/default-mime-types)]
    (if body
      {:status 200
       :headers {"content-type" mime-type}
       :body body}
      {:status 404
       :headers {"content-type" "text/html"}
       :body "<h1>Not found.</h1>"})))

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
     (biff/form
       {:method "POST"
        :action (str "/sites/" id "/publish")
        :class "inline-block"}
       [:button.hover:underline {:type "submit"} "Publish"])
     ui/interpunct
     [:a.hover:underline {:href (str "/sites/" id "/preview?path=/")
                          :target "_blank"}
      "Preview"]]]])

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
