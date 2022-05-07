(ns com.platypub.feat.lists
  (:require [com.biffweb :as biff :refer [q]]
            [com.platypub.mailgun :as mailgun]
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

(defn edit-list [{:keys [biff/db params] :as req}]
  (let [{:keys [id
                title
                theme
                reply-to]} params
        address (:list/address (xt/entity db (parse-uuid id)))]
    (mailgun/update! req address {:name title})
    (biff/submit-tx req
      [{:db/doc-type :list
        :db/op :update
        :xt/id (parse-uuid id)
        :list/title title
        :list/theme theme
        :list/reply-to reply-to}])
    {:status 303
     :headers {"location" (str "/newsletters/" id)}}))

(defn new-list [{:keys [mailgun/domain] :as req}]
  (let [id (random-uuid)
        address (str id "@" domain)]
    (mailgun/create! req address)
    (biff/submit-tx req
      [{:db/doc-type :list
        :xt/id id
        :list/address address
        :list/title ""
        :list/reply-to ""
        :list/theme "default-email"}])
    {:status 303
     :headers {"location" (str "/newsletters/" id)}}))

(defn delete-list [{:keys [path-params biff/db] :as req}]
  (mailgun/delete! req (:list/address (xt/entity db (parse-uuid (:id path-params)))))
  (biff/submit-tx req
    [{:xt/id (parse-uuid (:id path-params))
      :db/op :delete}])
  {:status 303
   :headers {"location" "/newsletters"}})

(defn edit-list-page [{:keys [path-params biff/db session] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        list-id (parse-uuid (:id path-params))
        lst (xt/entity db list-id)]
    (ui/nav-page
      {:base/head [[:script (biff/unsafe (slurp (io/resource "darkmode.js")))]]
       :current :newsletters
       :email email}
      [:.bg-gray-100.dark:bg-stone-800.dark:text-gray-50.flex-grow
       [:.max-w-screen-sm
        (biff/form
          {:id "edit"
           :action (str "/newsletters/" list-id)
           :hidden {:id list-id}
           :class '[flex flex-col flex-grow]}
          (ui/text-input {:id "title" :label "Title" :value (:list/title lst)})
          [:.h-3]
          (ui/text-input {:id "reply-to" :label "Reply To" :value (:list/reply-to lst)})
          [:.h-3]
          (ui/text-input {:id "theme" :label "Theme" :value (:list/theme lst)})
          [:.h-3]
          (ui/text-input {:id "address" :label "Address" :value (:list/address lst) :disabled true})
          [:.h-4]
          [:button.btn.w-full {:type "submit"} "Save"])
        [:.h-3]
        (biff/form
          {:onSubmit "return confirm('Delete newsletter?')"
           :method "POST"
           :action (str "/newsletters/" (:xt/id lst) "/delete")}
          [:button.text-red-600.hover:text-red-700 {:type "submit"} "Delete"])
        [:.h-6]]])))

(defn list-list-item [{:keys [list/title xt/id]}]
  [:.mb-4 [:a.link.block.text-lg {:href (str "/newsletters/" id)}
           (or (not-empty (str/trim title)) "[No title]")]])

(defn lists-page [{:keys [session biff/db] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        lists (q db
                 '{:find (pull list [*])
                   :where [[list :list/address]]})]
    (ui/nav-page
      {:current :newsletters
       :email email}
      (biff/form
        {:action "/newsletters"}
        [:button.btn {:type "submit"} "New newsletter"])
      [:.h-6]
      (->> lists
           (sort-by :list/title)
           (map list-list-item)))))

(def features
  {:routes ["" {:middleware [util/wrap-signed-in]}
            ["/newsletters" {:get lists-page
                             :post new-list}]
            ["/newsletters/:id"
             ["" {:get edit-list-page
                  :post edit-list}]
             ["/delete" {:post delete-list}]]]})
