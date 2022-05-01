(ns com.platypub.feat.posts
  (:require [com.biffweb :as biff :refer [q]]
            [com.platypub.netlify :as netlify]
            [com.platypub.ui :as ui]
            [com.platypub.util :as util]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [xtdb.api :as xt]
            [ring.middleware.anti-forgery :as anti-forgery]))

(defn edit-post [{:keys [params] :as req}]
  (let [{:keys [id
                html
                published
                tags
                slug
                description
                image
                canonical
                draft
                title]} params]
    (biff/submit-tx req
      [{:db/doc-type :post
        :db/op :update
        :xt/id (parse-uuid id)
        :post/title title
        :post/html html
        :post/published-at (edn/read-string published)
        :post/slug slug
        :post/status (if (= draft "on")
                       :draft
                       :published)
        :post/tags (->> (str/split tags #"\s+")
                        (remove empty?)
                        distinct
                        vec)
        :post/description description
        :post/image image
        :post/canonical canonical
        :post/edited-at :db/now}])
    {:status 303
     :headers {"location" (str "/app/posts/" id)}}))

(defn new-post [req]
  (let [id (random-uuid)]
    (biff/submit-tx req
      [{:db/doc-type :post
        :xt/id id
        :post/html ""
        :post/published-at :db/now
        :post/slug ""
        :post/status :draft
        :post/tags []
        :post/title ""
        :post/description ""
        :post/image ""
        :post/canonical ""
        :post/edited-at :db/now
        :post/authors []}])
    {:status 303
     :headers {"location" (str "/app/posts/" id)}}))

(defn delete-post [{:keys [path-params] :as req}]
  (biff/submit-tx req
    [{:xt/id (parse-uuid (:id path-params))
      :db/op :delete}])
  {:status 303
   :headers {"location" "/app"}})

(defn edit-post-page [{:keys [path-params
                              biff/db
                              tinycloud/api-key]
                       :or {api-key "no-api-key"}
                       :as req}]
  (let [post-id (parse-uuid (:id path-params))
        post (xt/entity db post-id)]
    (ui/base
      {:base/head [[:script (biff/unsafe (slurp (io/resource "darkmode.js")))]
                   [:script {:referrerpolicy "origin",
                             :src (str "https://cdn.tiny.cloud/1/" api-key "/tinymce/6/tinymce.min.js")}]
                   [:script (biff/unsafe (slurp (io/resource "tinymce_init.js")))]
                   [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/prism/1.17.1/themes/prism-okaidia.min.css"}]
                   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/prism/1.17.1/components/prism-core.min.js"}]
                   [:script {:src (str "https://cdnjs.cloudflare.com/ajax/libs/prism/1.17.1/plugins/"
                                       "autoloader/prism-autoloader.min.js")}]]}
      [:.bg-gray-100.dark:bg-stone-800.dark:text-gray-50.md:flex.flex-grow
       [:.md:w-80.mx-3
        [:.my-3 [:a.link {:href "/app"} "< Home"]]
        (biff/form
          {:id "edit"
           :action (str "/app/posts/" post-id)
           :hidden {:id post-id}
           :class '[flex flex-col flex-grow]}
          (ui/text-input {:id "title"
                          :label "Title"
                          :value (:post/title post)})
          [:.h-3]
          (ui/text-input {:id "slug"
                          :label "Slug"
                          :value (:post/slug post)})
          [:.h-3]
          (ui/checkbox {:id "draft"
                        :label "Draft"
                        :checked (not= :published (:post/status post))})
          [:.h-3]
          (ui/text-input {:id "published"
                          :label "Publish date"
                          :value (pr-str (:post/published-at post))})
          [:.h-3]
          (ui/text-input {:id "tags"
                          :label "Tags"
                          :value (str/join " " (:post/tags post))})
          [:.h-3]
          (ui/textarea {:id "description"
                        :label "Description"
                        :value (:post/description post)})
          [:.h-3]
          (ui/text-input {:id "image"
                          :label "Image"
                          :value (:post/image post)})
          (when-some [url (not-empty (:post/image post))]
            [:.mt-3.flex.justify-center
             [:img {:src url
                    :style {:max-height "10rem"}}]])
          [:.h-3]
          (ui/text-input {:id "canonical"
                          :label "Canonical URL"
                          :value (:post/canonical post)})
          [:.h-3]
          (ui/text-input {:id "edited"
                          :name nil
                          :label "Last saved"
                          :disabled true
                          :value (pr-str (:post/edited-at post))})
          [:.h-4]
          [:button.btn.w-full {:type "submit"} "Save"])
        [:.h-3]
        (biff/form
          {:onSubmit "return confirm('Delete post?')"
           :method "POST"
           :action (str "/app/posts/" (:xt/id post) "/delete")}
          [:button.text-red-600.hover:text-red-700 {:type "submit"} "Delete"])
        [:.h-6]]
       [:.max-w-screen-md.mx-auto.w-full
        [:textarea#content
         {:form "edit"
          :type "text"
          :name "html"
          :value (:post/html post)}]]
       [:.w-6]
       [:.h-3]])))

(defn post-list-item [{:keys [post/edited-at
                              post/published-at
                              post/html
                              post/title
                              post/status
                              post/tags
                              xt/id]}]
  [:.mb-4
   [:a.link.block {:href (str "/app/posts/" id)}
    (or (not-empty (str/trim title)) "[No title]")]
   [:.text-sm.text-stone-600.dark:text-stone-300
    (if (= status :draft)
      (str "edited " (biff/format-date edited-at "dd MMM yyyy, H:mm aa"))
      (str "published " (biff/format-date published-at "dd MMM yyyy, H:mm aa")))
    (when (not-empty (remove empty? tags))
      ui/interpunct)
    (str/join ", " tags)]])

(defn app [{:keys [session biff/db] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        posts (q db
                 '{:find (pull post [*])
                   :where [[post :post/html]]})
        [drafts published] (util/split-by #(= :published (:post/status %)) posts)]
    (ui/nav-page
      {:current :posts
       :email email}
      (biff/form
        {:action "/app/posts"}
        [:button.btn {:type "submit"} "New post"])
      [:.h-6]
      (when (not-empty drafts)
        (list
          [:.text-lg.my-2 "Drafts"]
          (->> drafts
               (sort-by :post/edited-by #(compare %2 %1))
               (map post-list-item))
          [:.h-2]))
      (when (not-empty published)
        [:.text-lg.my-2 "Published"])
      (->> published
           (sort-by :post/published-at #(compare %2 %1))
           (map post-list-item)))))

(defn upload-image [{:keys [s3/cdn] :as req}]
  (let [image-id (random-uuid)
        file-info (get-in req [:multipart-params "file"])
        url (str cdn "/" image-id)]
    (util/s3 req {:method "PUT"
                  :key image-id
                  :file (:tempfile file-info)
                  :headers {"x-amz-acl" "public-read"
                            "content-type" (:content-type file-info)}})
    (biff/submit-tx req
      [{:db/doc-type :image
        :xt/id image-id
        :image/url url
        :image/filename (:filename file-info)
        :image/uploaded-at :db/now}])
    {:status 200
     :headers {"content-type" "application/json"}
     :body {:location url}}))

(def features
  {:routes ["/app" {:middleware [util/wrap-signed-in]}
            ["" {:get app}]
            ["/images/upload" {:post upload-image}]
            ["/posts/:id"
             ["" {:get edit-post-page
                  :post edit-post}]
             ["/delete" {:post delete-post}]]
            ["/posts" {:post new-post}]]})
