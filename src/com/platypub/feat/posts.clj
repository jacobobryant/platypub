(ns com.platypub.feat.posts
  (:require [cheshire.core :as cheshire]
            [com.biffweb :as biff :refer [q]]
            [com.platypub.mailgun :as mailgun]
            [com.platypub.netlify :as netlify]
            [com.platypub.ui :as ui]
            [com.platypub.util :as util]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [xtdb.api :as xt]
            [ring.middleware.anti-forgery :as anti-forgery])
  (:import [io.github.furstenheim CopyDown]))

(defn title->slug [title]
  (-> title
      str/lower-case
      ; RFC 3986 reserved or unsafe characters in url
      (str/replace #"[/|]" "-")
      (str/replace #"[:?#\[\]@!$&'()*+,;=\"<>%{}\\^`]" "")
      (str/replace #"\s+" "-")))

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
                title
                site-id]} params]
    (biff/submit-tx req
      [{:db/doc-type :post
        :db/op :update
        :xt/id (parse-uuid id)
        :post/title title
        :post/html html
        :post/published-at (edn/read-string published)
        :post/slug (or (not-empty slug) (title->slug title))
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
        :post/sites (vector (parse-uuid site-id))
        :post/edited-at :db/now}])
    {:status 303
     :headers {"location" (str "/app/posts/" id)}}))


(defn new-post [{:keys [session] :as req}]
  (let [id (random-uuid)]
    (biff/submit-tx req
      [{:db/doc-type :post
        :xt/id id
        :post/user (:uid session)
        :post/html ""
        :post/published-at :db/now
        :post/slug ""
        :post/status :draft
        :post/tags []
        :post/title ""
        :post/description ""
        :post/image ""
        :post/canonical ""
        :post/edited-at :db/now}])
    {:status 303
     :headers {"location" (str "/app/posts/" id)}}))

(defn delete-post [{:keys [path-params] :as req}]
  (biff/submit-tx req
    [{:xt/id (parse-uuid (:id path-params))
      :db/op :delete}])
  {:status 303
   :headers {"location" "/app"}})

(defn send-page [{:keys [biff/db session path-params params] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        post-id (parse-uuid (:id path-params))
        post (xt/entity db post-id)
        lists (q db
                 '{:find (pull list [*])
                   :in [user]
                   :where [[list :list/user user]]}
                 (:uid session))]
    (ui/nav-page
      {:current :posts
       :email email}
      (when (= "true" (:sent params))
        [:div
         {:class '[bg-stone-200
                   dark:bg-stone-900
                   p-3
                   text-center
                   border-l-8
                   border-green-700]
          :_ "on load wait 5s then remove me"}
         "Message sent"])
      [:.flex
       (biff/form
         {:id "send"
          :action (str "/app/posts/" post-id "/send")
          :hidden {:post-id post-id}
          :class '[flex
                   flex-col
                   flex-grow
                   max-w-lg
                   w-full]}
         [:.text-lg.my-2 "Send newsletter"]
         (ui/text-input {:id "post"
                         :label "Post"
                         :value (:post/title post)
                         :disabled true})
         [:.h-3]
         (ui/select {:label "Newsletter"
                     :id "list"
                     :name "list-id"
                     :options (for [lst (sort-by :list/title lists)]
                                {:label (or (not-empty (:list/title lst)) "[No title]")
                                 :value (str (:xt/id lst))})})
         [:.h-3]
         [:label.block.text-sm.mb-1 {:for "addresses"} "Send test email"]
         [:.flex.gap-3
          (ui/text-input {:id "test-address"})
          [:button.btn-secondary {:type "submit" :name "send-test" :value "true"}
           "Send"]]
         [:.h-6]
         [:.flex.gap-3
          [:button.btn-secondary.flex-1
           {:type "submit"
            :formmethod "get"
            :formaction "preview"
            :formtarget "_blank"}
           "Preview"]
          [:button.btn.flex-1 {:type "submit"} "Send"]])])))

(defn html->md [html]
  (-> (.convert (CopyDown.) html)
      ;; Helps email clients render links correctly.
      (str/replace "(" "( ")
      (str/replace ")" " )")))

(defn render-email [{:keys [biff/db] {:keys [list-id post-id]} :params :as req}]
  (let [render-opts (assoc (util/get-render-opts req)
                           :list-id (parse-uuid list-id)
                           :post-id (parse-uuid post-id))
        post (xt/entity db (parse-uuid post-id))
        lst (xt/entity db (parse-uuid list-id))
        dir (str "themes/" (:list/theme lst))
        _ (biff/sh "chmod" "+x" "./render-email" :dir dir)
        msg (merge {:subject (:post/title post)}
                   (edn/read-string (biff/sh "./render-email"
                                             :in (pr-str render-opts)
                                             :dir dir))
                   {:to (:list/address lst)
                    :from (str (:list/title lst) " <doreply@" (:mailgun/domain req) ">")
                    :h:Reply-To (:list/reply-to lst)})]
    (cond-> msg
      (nil? (:text msg)) (assoc :text (html->md (:html msg)))
      true (assoc :html (biff/sh "npx" "juice"
                                 "--web-resources-images" "false"
                                 "/dev/stdin" "/dev/stdout"
                                 :in (:html msg))))))

(defn preview [{:keys [biff/db] {:keys [list-id post-id]} :params :as req}]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (:html (render-email req))})

(defn send! [{:keys [biff/db params] {:keys [send-test test-address]} :params :as req}]
  (mailgun/send! req (merge (render-email req)
                            (when send-test
                              {:to test-address})))
  {:status 303
   :headers {"location" "send?sent=true"}})

(defn edit-post-page [{:keys [path-params
                              biff/db
                              session
                              tinycloud/api-key]
                       :or {api-key "no-api-key"}
                       :as req}]
  (let [post-id (parse-uuid (:id path-params))
        post (xt/entity db post-id)
        sites (q db
                 '{:find (pull site [*])
                   :in [user]
                   :where [[site :site/user user]]}
                 (:uid session))]
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
        [:.flex.justify-between
         [:.my-3 [:a.link {:href "/app"} "< Home"]]
         [:.my-3 [:a.link {:href (str "/app/posts/" post-id "/send")} "Send"]]]
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
          [:h-3]
          (ui/select {:id "sites"
                      :name "site-id"
                      :label "Site"
                      :default (str (first (:post/sites post)))
                      :options (for [site (sort-by :site/title sites)]
                                 {:label (or (not-empty (:site/title site)) "[No title]")
                                  :value (str (:xt/id site))})})
          [:.h-4]
          [:button.btn.w-full {:type "submit"} "Save"])
        [:.h-3]
        [:.flex.justify-between
         (biff/form
           {:onSubmit "return confirm('Delete post?')"
            :method "POST"
            :action (str "/app/posts/" (:xt/id post) "/delete")}
           [:button.text-red-600.hover:text-red-700 {:type "submit"} "Delete"])]
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
                   :in [user]
                   :where [[post :post/user user]]}
                 (:uid session))
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

(defn upload-image [{:keys [session s3/cdn] :as req}]
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
        :image/user (:uid session)
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
             ["/delete" {:post delete-post}]
             ["/send" {:get send-page
                       :post send!}]
             ["/preview" {:get preview}]]
            ["/posts" {:post new-post}]]})
