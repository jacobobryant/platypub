(ns com.platypub.mailgun
  (:require [clj-http.client :as http]
            [cheshire.core :as cheshire]
            [com.platypub.util :as util]))

(def base-url "https://api.mailgun.net/v3")

(defn mailgun [{:keys [biff/secret] :as sys} method endpoint params]
  (http/request (merge {:method method
                        :url (str base-url endpoint)
                        :basic-auth ["api" (secret :mailgun/api-key)]}
                       params)))

(defn create! [sys address]
  (mailgun sys
           :post
           "/lists"
           {:form-params {:address address
                          :reply_preference "sender"}}))

(defn delete! [sys address]
  (mailgun sys
           :delete
           (str "/lists/" address)
           {}))

(defn update! [sys address params]
  (mailgun sys
           :put
           (str "/lists/" address)
           {:form-params params}))

(defn get-one [sys address]
  (mailgun sys
           :get
           (str "/lists/" address)
           {:as :json}))

(defn get-lists [sys]
  (mailgun sys
           :get
           "/lists/pages"
           {:as :json}))

(defn get-list-members [sys address params]
  (mailgun sys
           :get
           (str "/lists/" address "/members/pages")
           {:query-params (merge {:subscribed true
                                  :limit 1000} params)
            :as :json}))

(defn add-sub! [sys address params]
  (mailgun sys
           :post
           (str "/lists/" address "/members")
           {:form-params (merge {:upsert true} params)}))

(defn bulk-import! [sys address members]
  (mailgun sys
           :post
           (str "/lists/" address "/members.json")
           {:form-params {:members (cheshire/generate-string members)}}))

(defn send! [{:keys [mailgun/domain] :as sys} params]
  (mailgun sys
           :post
           (str "/" domain "/messages")
           {:form-params params}))
