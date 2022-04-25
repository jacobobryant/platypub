(ns com.platypub.feat.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.platypub.netlify :as netlify]
            [com.platypub.ui :as ui]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
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
                canonical]} params]
    (biff/submit-tx req
      [{:db/doc-type :post
        :db/op :update
        :xt/id (parse-uuid id)
        :post/html html
        :post/published-at (edn/read-string published)
        :post/slug slug
        :post/tags (->> (str/split tags #"\s+")
                        distinct
                        vec)
        :post/description description
        :post/image image
        :post/canonical canonical}])
    {:status 303
     :headers {"location" (str "/app/posts/" id)}}))

(defn edit-post-page [{:keys [path-params
                              biff/db
                              tinycloud/api-key]
                       :or {api-key "no-api-key"}
                       :as req}]
  (let [post-id (java.util.UUID/fromString (:id path-params))
        post (xt/entity db post-id)]
    (ui/base
      {:base/head [[:script {:referrerpolicy "origin",
                             :src (str "https://cdn.tiny.cloud/1/" api-key "/tinymce/6/tinymce.min.js")}]
                   [:script (biff/unsafe "tinymce.init({ selector: '#content', height: '100%', width: '100%' });")]]}
      [:.bg-gray-100.flex.flex-col.flex-grow
       [:.p-3 [:a.link {:href "/app"} "< Home"]]
       (biff/form
         {:action (str "/app/posts/" post-id)
          :hidden {:id post-id}
          :class '[flex flex-col flex-grow]}
         [:.flex.flex-row-reverse.flex-grow
          [:.w-6]
          [:.w-80
           (ui/text-input {:id "title"
                           :label "Title"
                           :value (:post/title post)})
           [:.h-3]
           (ui/text-input {:id "slug"
                           :label "Slug"
                           :value (:post/slug post)})
           [:.h-3]
           (ui/text-input {:id "published"
                           :label "Published"
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
                           :value (str/join " " (:post/tags post))})
           [:.h-3]
           (ui/text-input {:id "canonical"
                           :label "Canonical URL"
                           :value (:post/canonical post)})
           [:.h-3]
           (ui/text-input {:id "edited"
                           :name nil
                           :label "Last saved:"
                           :disabled true
                           :value (pr-str (:post/edited-at post))})
           [:.h-4]
           [:button.btn.w-full {:type "submit"} "Save"]]
          [:.w-6]
          [:.max-w-screen-sm.mx-auto.w-full
           [:textarea#content
            {:type "text"
             :name "html"
             :value (:post/html post)}]]
          [:.w-6]])
       [:.h-3]])))

(defn app [{:keys [session biff/db] :as req}]
  (let [{:user/keys [email foo bar]} (xt/entity db (:uid session))
        posts (q db
                 '{:find (pull post [*])
                   :where [[post :post/html]]})]
    (ui/page
      {}
      [:div "Signed in as " email ". "
       (biff/form
         {:action "/auth/signout"
          :class "inline"}
         [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
          "Sign out"])
       "."]
      [:.h-6]
      (biff/form
        {:action "/app/publish"}
        [:button.btn {:type "submit"} "Publish"])
      [:.h-3]
      [:ul
       (for [{:keys [post/edited-at
                     post/published-at
                     post/html
                     xt/id]} (sort-by :post/edited-at #(compare %2 %1) posts)]
         [:li [:a.link {:href (str "/app/posts/" id)}
               "Post last edited at " edited-at]])])))

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
            ["/posts/:id" {:get edit-post-page
                           :post edit-post}]
            ["/publish" {:post publish}]]
   :on-tx notify-clients})
