(ns com.platypub.cli.subscribers
  (:require
   [clojure.pprint :refer [print-table]]
   [com.platypub.util :as util]
   [clj-http.client :as http]))

(def base-url "https://api.mailgun.net/v3")

(defn mailgun [{:keys [mailgun/api-key]} method endpoint params]
  (http/request (merge {:method method
                        :url (str base-url endpoint)
                        :basic-auth ["api" (api-key)]}
                       params)))

(defn get-list-members [config]
  (mailgun config
           :get
           (str "/lists/" (:list/address config) "/members/pages")
           {:query-params {:subscribed true
                           :limit 1000}
            :as :json}))

(defn subscriber-record [item]
  (let [{:keys [address vars]} item
        {:keys [href joinedAt referrer]} vars]
    {:address address
     :joined-at joinedAt
     :href href
     :referrer referrer}))

(defn subscribers []
  (let [{:keys [list/address mailgun/api-key] :as config} (util/read-config)
        members (->> (get-list-members config)
                     :body
                     :items
                     (mapv subscriber-record)
                     distinct
                     (sort #(compare (:joined-at %2) (:joined-at %1))))]
    (print-table members)))
