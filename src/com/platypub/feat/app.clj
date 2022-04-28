(ns com.platypub.feat.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.platypub.netlify :as netlify]
            [com.platypub.ui :as ui]
            [com.platypub.util :as util]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [ring.middleware.anti-forgery :as anti-forgery]
            [cheshire.core :as cheshire]))

(defn set-foo [{:keys [session params] :as req}]
  (biff/submit-tx req
    [{:db/op :update
      :db/doc-type :user
      :xt/id (:uid session)
      :user/foo (:foo params)}])
  {:status 303
   :headers {"location" "/app"}})

(defn bar-form [{:keys [value]}]
  (biff/form
    {:hx-post "/app/set-bar"
     :hx-swap "outerHTML"}
    [:label.block {:for "bar"} "Bar: "
     [:span.font-mono (pr-str value)]]
    [:.h-1]
    [:.flex
     [:input.w-full#bar {:type "text" :name "bar" :value value}]
     [:.w-3]
     [:button.btn {:type "submit"} "Update"]]
    [:.h-1]
    [:.text-sm.text-gray-600
     "This demonstrates updating a value with HTMX."]))

(defn set-bar [{:keys [session params] :as req}]
  (biff/submit-tx req
    [{:db/op :update
      :db/doc-type :user
      :xt/id (:uid session)
      :user/bar (:bar params)}])
  (biff/render (bar-form {:value (:bar params)})))

(defn message [{:msg/keys [text sent-at]}]
  [:.mt-3 {:_ "init send newMessage to #message-header"}
   [:.text-gray-600 (biff/format-date sent-at "dd MMM yyyy HH:mm:ss")]
   [:div text]])

(defn notify-clients [{:keys [com.platypub/chat-clients]} tx]
  (doseq [[op & args] (::xt/tx-ops tx)
          :when (= op ::xt/put)
          :let [[doc] args]
          :when (contains? doc :msg/text)
          :let [html (rum/render-static-markup
                       [:div#messages {:hx-swap-oob "afterbegin"}
                        (message doc)])]
          ws @chat-clients]
    (jetty/send! ws html)))

(defn send-message [{:keys [session] :as req} {:keys [text]}]
  (let [{:keys [text]} (cheshire/parse-string text true)]
    (biff/submit-tx req
      [{:db/doc-type :msg
        :msg/user (:uid session)
        :msg/text text
        :msg/sent-at :db/now}])))

(defn chat [{:keys [biff/db]}]
  (let [messages (q db
                    '{:find (pull msg [*])
                      :in [t0]
                      :where [[msg :msg/sent-at t]
                              [(<= t0 t)]]}
                    (biff/add-seconds (java.util.Date.) (* -60 10)))]
    [:div {:hx-ws "connect:/app/chat"}
     [:form.mb0 {:hx-ws "send"
                 :_ "on submit set value of #message to ''"}
      [:label.block {:for "message"} "Write a message"]
      [:.h-1]
      [:textarea.w-full#message {:name "text"}]
      [:.h-1]
      [:.text-sm.text-gray-600
       "Sign in with an incognito window to have a conversation with yourself."]
      [:.h-2]
      [:div [:button.btn {:type "submit"} "Send message"]]]
     [:.h-6]
     [:div#message-header
      {:_ "on newMessage put 'Messages sent in the past 10 minutes:' into me"}
      (if (empty? messages)
        "No messages yet."
        "Messages sent in the past 10 minutes:")]
     [:div#messages
      (map message (sort-by :msg/sent-at #(compare %2 %1) messages))]]))

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
                   [:script (biff/unsafe "tinymce.init({ selector: '#content',
                                         skin: (darkModeOn ? 'oxide-dark' : 'oxide'),
                                         content_css: (darkModeOn ? 'dark' : 'default'),
                                         height: '100%', width: '100%' });")]]}
      [:.bg-gray-100.dark:bg-stone-800.dark:text-gray-50.flex.flex-col.flex-grow
       [:.p-3 [:a.link {:href "/app"} "< Home"]]
       [:.flex.flex-row-reverse.flex-grow
        [:.w-6]
        [:.w-80
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
                           :value (str/join " " (:post/image post))})
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
           [:button.text-red-600 {:type "submit"} "Delete"])]
        [:.w-6]
        [:.max-w-screen-sm.mx-auto.w-full
         [:textarea#content
          {:form "edit"
           :type "text"
           :name "html"
           :value (:post/html post)}]]
        [:.w-6]]
       [:.h-3]]
      [:script (biff/unsafe (str
                              "document.body.addEventListener('htmx:configRequest', (event) => {
                              event.detail.headers['X-CSRF-Token'] = '"
                              anti-forgery/*anti-forgery-token*
                              "';})"))])))

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
  (let [{:user/keys [email foo bar]} (xt/entity db (:uid session))
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

(defn wrap-signed-in [handler]
  (fn [{:keys [session] :as req}]
    (if (some? (:uid session))
      (handler req)
      {:status 303
       :headers {"location" "/"}})))

(defn ws-handler [{:keys [com.platypub/chat-clients] :as req}]
  {:status 101
   :headers {"upgrade" "websocket"
             "connection" "upgrade"}
   :ws {:on-connect (fn [ws]
                      (swap! chat-clients conj ws))
        :on-text (fn [ws text-message]
                   (send-message req {:ws ws :text text-message}))
        :on-close (fn [ws status-code reason]
                    (swap! chat-clients disj ws))}})

(defn publish [{:keys [biff/db] :as req}]
  (let [post (first (q db
                       '{:find (pull post [*])
                         :where [[post :post/html]]}))
        site-id "d51accde-a88f-48b3-ba15-5defbf798df2"]
    (io/make-parents (io/file "storage/site/_"))
    (spit "storage/site/index.html" (:post/html post))
    (netlify/deploy! {:api-key (:netlify/api-key req)
                      :site-id site-id
                      :dir "storage/site"}))
  {:status 303
   :headers {"location" "/app"}})

(def features
  {:routes ["/app" {:middleware [wrap-signed-in]}
            ["" {:get app}]
            ["/set-foo" {:post set-foo}]
            ["/set-bar" {:post set-bar}]
            ["/chat" {:get ws-handler}]
            ["/posts/:id"
             ["" {:get edit-post-page
                  :post edit-post}]
             ["/delete" {:post delete-post}]]
            ["/posts" {:post new-post}]
            ["/publish" {:post publish}]]
   :on-tx notify-clients})
