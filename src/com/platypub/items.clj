(ns com.platypub.items
  (:require [cheshire.core :as cheshire]
            [com.biffweb :as biff :refer [q]]
            [com.platypub.mailgun :as mailgun]
            [com.platypub.middleware :as mid]
            [com.platypub.netlify :as netlify]
            [com.platypub.ui :as ui]
            [com.platypub.util :as util]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [xtdb.api :as xt]
            [ring.middleware.anti-forgery :as anti-forgery])
  (:import [io.github.furstenheim CopyDown]))

(defn recipient-count [sys list]
  (->> (mailgun/get-list-members sys (:list/address list) {})
       :body
       :items
       count))

(defn html->md [html]
  (-> (.convert (CopyDown.) html)
      ;; Helps email clients render links correctly.
      (str/replace "(" "( ")
      (str/replace ")" " )")))

(defn render-email [{:keys [biff/db params site] lst :list :as sys}]
  (let [theme (util/resolve-theme sys (:site/theme site))
        msg (merge ((:render-email theme) (util/get-render-opts sys))
                   {:to (:list/address lst)
                    :from (str (:list/title lst) " <doreply@" (:mailgun/domain sys) ">")
                    :h:Reply-To (:list/reply-to lst)})]
    (cond-> msg
      (nil? (:text msg)) (assoc :text (html->md (:html msg)))
      true (assoc :html (biff/sh "npx" "juice"
                                 "--web-resources-images" "false"
                                 "/dev/stdin" "/dev/stdout"
                                 :in (:html msg))))))

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

(defn create [{:keys [user site item-spec] :as sys}]
  (let [id (random-uuid)]
    (biff/submit-tx sys
      [(into
        {:db/doc-type :item
         :xt/id id
         :item/user (:xt/id user)
         :item/sites #{(:xt/id site)}}
        (for [k (:fields item-spec)
              :let [{:keys [type default]} (get-in site [:site.config/fields k])]]
          [(util/add-prefix "item.custom." k)
           (or (when-not (vector? default)
                 default)
               (case type
                 :instant :db/now
                 :boolean false
                 :tags []
                 ""))]))])
    {:status 303
     :headers {"location" (util/make-url "site" (:xt/id site) (:slug item-spec) id)}}))

(defn edit-page [{:keys [biff/secret biff/db user site item-spec item] :as sys}]
  (let [html-key (->> (:fields item-spec)
                      (filter #(= (get-in site [:site.config/fields % :type]) :html))
                      first)]
    (ui/base
     {:base/head (concat
                  [[:script (biff/unsafe (slurp (io/resource "darkmode.js")))]]
                  (when (some? html-key)
                    [[:script {:referrerpolicy "origin",
                               :src (str "https://cdn.tiny.cloud/1/"
                                         (or (secret :tinycloud/api-key) "no-api-key")
                                         "/tinymce/6/tinymce.min.js")}]
                     [:script (biff/unsafe (slurp (io/resource "tinymce_init.js")))]
                     [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/prism/1.17.1/themes/prism-okaidia.min.css"}]
                     [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/prism/1.17.1/components/prism-core.min.js"}]
                     [:script {:src (str "https://cdnjs.cloudflare.com/ajax/libs/prism/1.17.1/plugins/"
                                         "autoloader/prism-autoloader.min.js")}]]))}
     [:.bg-gray-100.dark:bg-stone-800.dark:text-gray-50.md:flex.flex-grow
      [:.md:w-80.mx-3
       [:.flex
        [:.my-3 [:a.link {:href (util/make-url "site" (:xt/id site) (:slug item-spec))}
                 "< " (:label item-spec) "s"]]
        [:.flex-grow]
        (when (:sendable item-spec)
          [:.my-3 [:a.link {:href (util/make-url "site" (:xt/id site) (:slug item-spec) (:xt/id item) "send")}
                   "Send"]])]
       (biff/form
        {:id "edit"
         :action (util/make-url "site" (:xt/id site) (:slug item-spec) (:xt/id item))
         :class '[flex flex-col flex-grow]}
        (for [k (:fields item-spec)
              :when (not= (get-in site [:site.config/fields k :type]) :html)]
          (list
           (ui/custom-field sys k)
           [:.h-3]))
        [:.h-1]
        [:button.btn.w-full {:type "submit"} "Save"])
       [:.h-3]
       [:.flex.justify-between
        (biff/form
         {:onSubmit "return confirm('Delete item?')"
          :action (util/make-url "site" (:xt/id site) (:slug item-spec) (:xt/id item) "delete")}
         [:button.text-red-600.hover:text-red-700 {:type "submit"} "Delete"])]
       [:.h-6]]
      (when (some? html-key)
        [:.max-w-screen-md.mx-auto.w-full
         [:textarea#content
          {:form "edit"
           :type "text"
           :name (name html-key)
           :value (get item (util/add-prefix "item.custom." html-key))}]])
      [:.w-6.h-3]])))

(defn edit [{:keys [user site item-spec item params] :as sys}]
  (biff/submit-tx sys
    [(into
      {:db/doc-type :item
       :xt/id (:xt/id item)
       :db/op :update}
      (util/params->custom-fields sys))])
  {:status 303
   :headers {"location" (util/make-url "site" (:xt/id site) (:slug item-spec) (:xt/id item))}})

(defn delete [{:keys [site item-spec item] :as sys}]
  (biff/submit-tx sys
    [{:xt/id (:xt/id item)
      :db/op :delete}])
  {:status 303
   :headers {"location" (util/make-url "site" (:xt/id site) (:slug item-spec))}})

(defn send-page [{:keys [biff/db user site item-spec item params] :as sys}]
  (let [lists (->> (q db
                      '{:find (pull list [*])
                        :in [user]
                        :where [[list :list/user user]]}
                      (:xt/id user))
                   (map (fn [lst]
                          (assoc lst :list/n-subscribers (recipient-count sys lst)))))
        list-id (or (some-> (:list-id params) parse-uuid)
                    (->> lists
                         (filter #(contains? (set (:list/sites %)) (:xt/id site)))
                         first
                         :xt/id))]
    (ui/nav-page
     sys
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
        :class '[flex
                 flex-col
                 flex-grow
                 max-w-lg
                 w-full]}
       [:.text-lg.my-2 "Send newsletter"]
       (ui/text-input {:id "item"
                       :label (:label item-spec)
                       :value (get item (util/add-prefix "item.custom." (:render/label item-spec)))
                       :disabled true})
       [:.h-3]
       (ui/select {:label "Newsletter"
                   :id "list"
                   :name "list-id"
                   :default list-id
                   :options (for [lst (sort-by :list/title lists)]
                              {:label (str (or (not-empty (:list/title lst)) "[No title]")
                                           " (" (:list/n-subscribers lst) " subscribers)")
                               :value (:xt/id lst)})})
       [:.h-3]
       [:label.block.text-sm.mb-1 {:for "addresses"} "Send test email"]
       [:.flex.gap-3
        (ui/text-input {:id "test-address"})
        [:button.btn-secondary {:hx-post (util/make-url "site"
                                                        (:xt/id site)
                                                        (:slug item-spec)
                                                        (:xt/id item)
                                                        "send?send-test=true")
                                :hx-target "body"}
         "Send"]]
       [:.h-6]
       [:.flex.gap-3
        [:button.btn-secondary.flex-1
         {:type "submit"
          :formmethod "get"
          :formaction "preview"
          :formtarget "_blank"}
         "Preview"]
        [:button.btn.flex-1 {:hx-confirm "Send newsletter?"
                             :hx-post (util/make-url "site"
                                                     (:xt/id site)
                                                     (:slug item-spec)
                                                     (:xt/id item)
                                                     "send")
                             :hx-target "body"}
         "Send"]])])))

(defn preview [sys]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (:html (render-email sys))})

(defn send! [{:keys [biff/db params] {:keys [send-test test-address list-id]} :params :as sys}]
  (mailgun/send! sys
                 (merge (render-email sys)
                        (when send-test
                          {:to test-address})))
  {:status 200
   :headers {"hx-redirect" (str "send?sent=true&list-id=" list-id)}})

(defn item-summary [{:keys [biff/db item-spec site] :as sys} item show]
  [:.mb-4
   [:div
    [:a.link {:href (util/make-url "/site"
                                   (:xt/id site)
                                   (:slug item-spec)
                                   (:xt/id item))}
     (or (not-empty (str/trim (get item
                                   (util/add-prefix
                                    "item.custom."
                                    (:render/label item-spec))
                                   "")))
         "[No title]")]]
   [:.text-sm.text-stone-600.dark:text-stone-300
    (util/join
     ui/interpunct
     (for [k show
           :let [{:keys [label type]} (get-in sys [:site :site.config/fields k])
                 value (get item (util/add-prefix "item.custom." k))]
           :when (util/something? value)]
       (if (= k :edited-at)
         (list "Last edited: " (biff/format-date (util/last-edited db (:xt/id item))
                                                 "dd MMM yyyy, H:mm aa"))
         (list label ": "
               (str
                (case type
                  :tags (str/join ", " value)
                  :instant (biff/format-date value "dd MMM yyyy, H:mm aa")
                  value))))))]])

(defn site-list-item [{:keys [site/title
                              xt/id
                              site.config/items]}
                      item-spec]
  [:.md:hidden.flex.items-center.mt-7
   [:div [:a.link.text-lg {:href (str "/sites/" id)}
          (or (not-empty (str/trim title)) "[No title]")]
    [:span.md:hidden
     " " biff/emdash " "
     (util/join
      ", "
      (for [{:keys [slug label]} items]
        (if (= slug (:slug item-spec))
          [:span.text-lg (str label "s")]
          [:a.link.text-lg {:href (util/make-url "site" id slug)}
           (str label "s")])))]]])

(defn items-page [{:keys [session biff/db user site item-spec] :as req}]
  (let [items (util/q-items db user site item-spec)]
    (ui/nav-page
     req
     (biff/form
      {:action (util/make-url "/site" (:xt/id site) (:slug item-spec))}
      [:button.btn {:type "submit"} (str "New " (str/lower-case (:label item-spec)))])
     (site-list-item site item-spec)
     [:.h-6]
     (for [{:keys [label match order-by show]} (:render/sections item-spec)
           :let [items (->> items
                            (filter #(util/match? match %))
                            (sort-by identity (util/order-by-fn order-by)))]]
       (list
        (when label
          [:.text-lg.my-2 label])
        (when (empty? items)
          [:.text-stone-600.dark:text-stone-400 "No items yet."])
        (for [item items]
          (item-summary req item show))
        [:.h-3])))))

(defn app [{:keys [sites]}]
  {:status 303
   :headers {"location" "/sites/"}})

(def plugin
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app" {:get app}]
            ["/app/images/upload" {:post upload-image}]
            ["/site/:site-id/:item-slug"
             ["" {:get items-page
                  :post create}]
             ["/:item-id"
              ["" {:get edit-page
                   :post edit}]
              ["/delete" {:post delete}]
              ["/send" {:get send-page
                        :post send!}]
              ["/preview" {:get preview}]]]]})
