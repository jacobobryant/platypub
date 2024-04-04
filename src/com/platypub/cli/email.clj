(ns com.platypub.cli.email
  (:require [com.platypub.util :as util]
            [clojure.java.io :as io]
            [lambdaisland.hiccup :as h]
            [hato.client :as hato]))

(defn send-email [path address]
  (let [{:keys [list/reply-to mailgun/domain mailgun/api-key]
         list-title :list/title
         :as config} (util/read-config)
        post (util/read-md-file config (io/file path))
        html ((requiring-resolve (:email/theme config)) (assoc config :post post))]
    (println "Sending" (pr-str (:title post)) "to" (pr-str address))
    (hato/post (str "https://api.mailgun.net/v3/" domain "/messages")
               {:basic-auth {:user "api" :pass (api-key)}
                :form-params {:html (h/render html)
                              :subject (:title post)
                              :to address
                              :from (str list-title " <doreply@" domain ">")
                              :h:Reply-To reply-to}})))

(defn publish-email [path]
  #_(send-email path (:list/address (util/read-config))))
