(ns com.platypub.mailgun
  (:require [clj-http.client :as http]
            [cheshire.core :as cheshire]))

(def base-url "https://api.mailgun.net/v3")

(defn create! [{:keys [mailgun/api-key mailgun/domain]} address]
  (http/post (str base-url "/lists")
             {:basic-auth ["api" api-key]
              :form-params {:address address
                            :reply_preference "sender"}}))

(defn delete! [{:keys [mailgun/api-key]} address]
  (http/delete (str base-url "/lists/" address)
               {:basic-auth ["api" api-key]}))

(defn update! [{:keys [mailgun/api-key]} address params]
  (http/put (str base-url "/lists/" address)
            {:basic-auth ["api" api-key]
             :form-params params}))

(defn get-one [{:keys [mailgun/api-key]} address]
  (http/get (str base-url "/lists/" address)
            {:basic-auth ["api" api-key]
             :as :json}))

(defn get-lists [{:keys [mailgun/api-key]}]
  (http/get (str base-url "/lists/pages")
            {:basic-auth ["api" api-key]
             :as :json}))

(defn get-list-members [{:keys [mailgun/api-key]} address params]
  (http/get (str base-url "/lists/" address "/members/pages")
            {:basic-auth ["api" api-key]
             :form-params (merge {:limit 1000})
             :as :json}))

(defn add-sub! [{:keys [mailgun/api-key]} address params]
  (http/post (str base-url "/lists/" address "/members")
             {:basic-auth ["api" api-key]
              :form-params (merge {:upsert true} params)}))

(defn bulk-import! [{:keys [mailgun/api-key]} address members]
  (http/post (str base-url "/lists/" address "/members.json")
             {:basic-auth ["api" api-key]
              :form-params {:members (cheshire/generate-string members)}}))

(defn send! [{:keys [mailgun/api-key mailgun/domain]} params]
  (http/post (str base-url "/" domain "/messages")
             {:basic-auth ["api" api-key]
              :form-params params}))
