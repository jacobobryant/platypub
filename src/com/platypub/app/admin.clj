(ns com.platypub.app.admin
  (:require [clojure.set :as set]
            [com.biffweb.admin :as biff.admin]
            [com.biffweb.sqlite :as biff.sqlite]
            [com.platypub.lib.email :as lib.email]))

(defn- get-users [ctx]
  (->> (biff.sqlite/execute ctx {:select   [:user/id
                                            :user/email
                                            :user/joined-at]
                                 :from     :user
                                 :order-by [[:user/joined-at :desc]]})
       (mapv #(set/rename-keys % {:user/id        :user-id
                                  :user/email     :email
                                  :user/joined-at :joined-at}))))

(defn- get-usage-events [_ctx]
  ;; If you want to monitor usage, return maps from the past 37 days with keys
  ;; :user-id (any) and :instant (Instant).
  [])

(defn- get-revenue-events [_ctx]
  ;; If your app has revenue, return maps from the past 30 days with keys
  ;; :revenue (number) and :instant (Instant).
  [])

(def module
  (biff.admin/module
   {:biff.admin/get-usage-events   #'get-usage-events
    :biff.admin/get-revenue-events #'get-revenue-events
    :biff.admin/get-users          #'get-users
    :biff.admin/send-email         #'lib.email/send-email}))
