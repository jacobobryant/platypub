(ns com.platypub.api.mock-mailersend
  (:require [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]))

(defpath send-email-path "/_mock/mailersend/v1/email")
(defpath activities-path "/_mock/mailersend/v1/activity/email")

(defpipeline reset-state
  (fn [{:keys [platypub/mock-mailersend-state]}]
    [:platypub/reset-atom
     mock-mailersend-state
     {:emails [], :activities []}]))

(defpipeline send-email
  (fn [{:keys         [params
                       headers
                       platypub/mock-mailersend-enabled
                       platypub/mock-mailersend-state]
        :biff.fx/keys [random-uuid7-seq]}]
    (if-not mock-mailersend-enabled
      {:biff.fx/return {:status 404}}
      {:_store
       [:platypub/swap-atom
        mock-mailersend-state
        update
        :emails
        conj
        (assoc params :request-headers headers)]

       :biff.fx/return
       {:status  202
        :headers {"x-message-id" (str (first random-uuid7-seq))}
        :body    {:message "Queued"}}})))

(defpipeline activities
  (fn [{:keys [platypub/mock-mailersend-enabled
               platypub/mock-mailersend-state]}]
    (if mock-mailersend-enabled
      {:state [:platypub/deref mock-mailersend-state]}
      {:biff.fx/return {:status 404}}))

  (fn [_ctx {:keys [state]}]
    {:status 200, :body {:data (:activities state)}}))

(def module
  {:biff.core/init
   {:platypub/mock-mailersend-state (atom {:emails [], :activities []})}

   :biff.ring/api-routes
   [[(send-email-path) {:post send-email}]
    [(activities-path) {:get activities}]]})
