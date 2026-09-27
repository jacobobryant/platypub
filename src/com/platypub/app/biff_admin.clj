(ns com.platypub.app.biff-admin
  (:require [clojure.set :as set]
            [com.biffweb.admin :as biff.admin]
            [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.email :as lib.email]))

(defpipeline get-users
  [:biff.graph.fx/query
   [{:global/users [:user/id :user/email :user/joined-at]}]]

  (fn [_ctx result]
    (->> (:global/users result)
         (mapv #(set/rename-keys % {:user/id        :user-id
                                    :user/email     :email
                                    :user/joined-at :joined-at})))))

(defn- get-usage-events [_ctx]
  ;; If you want to monitor usage, return maps from the past 37 days with keys
  ;; :user-id (any) and :instant (Instant).
  [])

(defn- get-revenue-events [_ctx]
  ;; If your app has revenue, return maps from the past 30 days with keys
  ;; :revenue (number) and :instant (Instant).
  [])

(def module
  (let [send-email   #'lib.email/send-email
        admin-module (biff.admin/module
                      {:biff.admin/get-usage-events   #'get-usage-events
                       :biff.admin/get-revenue-events #'get-revenue-events
                       :biff.admin/get-users          #'get-users
                       :biff.admin/send-email         send-email})]
    (update admin-module :biff.core/init
            (fn [init]
              (fn [modules-var]
                (assoc (init modules-var)
                       :biff.admin/send-email send-email))))))
