(ns com.platypub.lists
  (:require [com.biffweb :as biff :refer [q]]
            [com.platypub.mailgun :as mailgun]
            [com.platypub.middleware :as mid]
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
                reply-to
                mailing-address
                site-id]} params
        address (:list/address (xt/entity db (parse-uuid id)))]
    (mailgun/update! req address {:name (or (not-empty title) "no title")})
    (biff/submit-tx req
      [{:db/doc-type :list
        :db/op :update
        :xt/id (parse-uuid id)
        :list/title title
        :list/reply-to reply-to
        :list/mailing-address mailing-address
        :list/sites (vector (parse-uuid site-id))}])
    {:status 303
     :headers {"location" (str "/newsletters/" id)}}))

(defn new-list [{:keys [mailgun/domain session] :as req}]
  (let [id (random-uuid)
        address (str id "@" domain)]
    (mailgun/create! req address)
    (biff/submit-tx req
      [{:db/doc-type :list
        :xt/id id
        :list/user (:uid session)
        :list/address address
        :list/title ""
        :list/reply-to ""
        :list/mailing-address ""}])
    {:status 303
     :headers {"location" (str "/newsletters/" id)}}))

(defn delete-list [{:keys [path-params biff/db] :as req}]
  (mailgun/delete! req (:list/address (xt/entity db (parse-uuid (:list-id path-params)))))
  (biff/submit-tx req
    [{:xt/id (parse-uuid (:list-id path-params))
      :db/op :delete}])
  {:status 303
   :headers {"location" "/newsletters"}})

(defn list-subscriber-item [{:keys [address joined-at referrer href]}]
  (let [joined-at (some-> joined-at
                          java.time.Instant/parse
                          java.util.Date/from
                          (util/format-date "yyyy-MM-dd"))]
    [:tr
     [:td.pr-3 address]
     [:td.pr-3 joined-at]
     [:td.pr-3 referrer]
     [:td href]]))

(defn subscriber-record [item]
  (let [{:keys [address vars]} item
        {:keys [href joinedAt referrer]} vars]
    {:address address
     :joined-at joinedAt
     :href href
     :referrer referrer}))

(defn subscribers [{:keys [biff/db path-params params session] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        newsletter-id (parse-uuid (:list-id path-params))
        newsletter (xt/entity db newsletter-id)
        items (->> (mailgun/get-list-members req (:list/address newsletter) params)
                   :body
                   :items
                   (map subscriber-record)
                   set
                   (sort #(compare (:joined-at %2) (:joined-at %1))))]
    (ui/nav-page
     (merge req
            {:base/head [[:script (biff/unsafe (slurp (io/resource "darkmode.js")))]]
             :current :newsletters
             :email email})
     [:.bg-gray-100.dark:bg-stone-800.dark:text-gray-50.flex-grow
      [:div [:a.link.text-lg {:href (str "/newsletters/" newsletter-id)}
             (or (not-empty (str/trim (:list/title newsletter))) "[No title]")]]
      [:div.text-sm [:span.mr-3 "Mailgun address"] (:list/address newsletter)]
      [:div.pt-4.pb-3 "Subscribers: " biff/nbsp (count items)]
      [:table
       [:thead.text-sm
        [:tr.text-left
         [:th "Email address"]
         [:th "Joined"]
         [:th "Referrer"]
         [:th "Page"]]]
       [:tbody
        (map list-subscriber-item items)]]])))

(defn edit-list-page [{:keys [path-params biff/db session] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        list-id (parse-uuid (:list-id path-params))
        lst (xt/entity db list-id)
        sites (q db
                 '{:find (pull site [*])
                   :in [user]
                   :where [[site :site/user user]]}
                 (:uid session))]
    (ui/nav-page
     (merge req
            {:base/head [[:script (biff/unsafe (slurp (io/resource "darkmode.js")))]]
             :current :newsletters
             :email email})
     [:.bg-gray-100.dark:bg-stone-800.dark:text-gray-50.flex-grow
      [:.max-w-screen-sm
       (biff/form
        {:id "edit"
         :action (str "/newsletters/" list-id)
         :hidden {:id list-id}
         :class '[flex flex-col flex-grow]}
        (ui/text-input {:id "address" :label "Mailgun address" :value (:list/address lst) :disabled true})
        [:.h-3]
        (ui/text-input {:id "title" :label "Title" :value (:list/title lst)})
        [:.h-3]
        (ui/text-input {:id "reply-to" :label "Reply To" :value (:list/reply-to lst)})
        [:.h-3]
        (ui/text-input {:id "mailing-address" :label "Mailing address" :value (:list/mailing-address lst)})
        [:.h-3]
        (ui/select {:id "sites"
                    :name "site-id"
                    :label "Site"
                    :default (str (first (:list/sites lst)))
                    :options (for [site (sort-by :site/title sites)]
                               {:label (or (not-empty (:site/title site)) "[No title]")
                                :value (str (:xt/id site))})})
        [:.h-4]
        [:button.btn.w-full {:type "submit"} "Save"])
       [:.h-3]
       (biff/form
        {:onSubmit "return confirm('Delete newsletter?')"
         :method "POST"
         :action (str "/newsletters/" (:xt/id lst) "/delete")}
        [:button.text-red-600.hover:text-red-700 {:type "submit"} "Delete"])
       [:.h-6]]])))

(defn list-list-item [{:keys [list/title list/sites xt/id]}]
  [:.mb-4
   [:div [:a.link.text-lg {:href (str "/newsletters/" id)}
          (or (not-empty (str/trim title)) "[No title]")]]
   [:.text-sm.text-stone-600.dark:text-stone-300
    [:a.hover:underline {:href (str "/newsletters/" id "/subscribers")} "Subscribers"]
    (when (not-empty sites)
      ui/interpunct)
    (-> sites first :site/title)]])

(defn lists-page [{:keys [session biff/secret biff/db] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))
        lists (q db
                 '{:find (pull list [* {:list/sites [*]}])
                   :in [user]
                   :where [[list :list/user user]]}
                 (:uid session))]
    (ui/nav-page
     (merge req
            {:current :newsletters
             :email email})
     (biff/form
      {:action "/newsletters"}
      (when (nil? (secret :mailgun/api-key))
        [:p "You need to enter a Mailgun API key"])
      [:button.btn {:type "submit"
                    :disabled (nil? (secret :mailgun/api-key))} "New newsletter"])
     [:.h-6]
     (->> lists
          (sort-by :list/title)
          (map list-list-item)))))

(def plugin
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/newsletters" {:get lists-page
                             :post new-list}]
            ["/newsletters/:list-id"
             ["" {:get edit-list-page
                  :post edit-list}]
             ["/delete" {:post delete-list}]
             ["/subscribers" {:get subscribers}]]]})
