(ns com.platypub.api.mock-mailersend
  (:require [clojure.string :as str]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]))

(defpath send-email-path "/_mock/mailersend/v1/email")
(defpath send-bulk-email-path "/_mock/mailersend/v1/bulk-email")
(defpath activities-path "/_mock/mailersend/v1/activity/:domain-id")
(defpath emails-path "/_mock/mailersend/emails")

(defpipeline reset-state
  (fn [{:keys [platypub/mock-mailersend-state]}]
    [:platypub/reset-atom
     mock-mailersend-state
     {:emails [], :activities []}]))

(defpipeline send-email
  (fn [{:keys         [params
                       headers
                       platypub/mock-mailersend-enabled]
        :biff.fx/keys [random-uuid7-seq]}]
    (if-not mock-mailersend-enabled
      {:biff.fx/return {:status 404}}
      {:_store
       [:platypub.fx/swap!
        :platypub/mock-mailersend-state
        `update
        :emails
        `conj
        (assoc params :request-headers headers)]

       :_print [:platypub.fx/print
                (str "\n--- outgoing email ---\n"
                     (:text params)
                     "\n--- end email ---")]

       :biff.fx/return
       {:status  202
        :headers {"x-message-id" (str (first random-uuid7-seq))}
        :body    {:message "Queued"}}})))

(defpipeline send-bulk-email
  (fn [{:keys         [body-params
                       json-params
                       params
                       headers
                       platypub/mock-mailersend-enabled]
        :biff.fx/keys [random-uuid7-seq]}]
    (let [emails (or (when (sequential? body-params) body-params)
                     (when (sequential? json-params) json-params)
                     (when (sequential? params) params))]
      (if-not (and mock-mailersend-enabled (seq emails))
        {:biff.fx/return {:status (if mock-mailersend-enabled 422 404)}}
        {:_store
         [:platypub.fx/swap!
          :platypub/mock-mailersend-state
          `update
          :emails
          `into
          (mapv #(assoc % :request-headers headers) emails)]

         :_print
         [:platypub.fx/print
          (str "\n--- outgoing bulk email ---\n"
               (str/join "\n\n" (mapv :text emails))
               "\n--- end bulk email ---")]

         :biff.fx/return
         {:status 202
          :body   {:message       "The bulk email is being processed."
                   :bulk_email_id (str (first random-uuid7-seq))}}}))))

(defpipeline emails
  (fn [{:keys [platypub/mock-mailersend-enabled
               platypub/mock-mailersend-state]}]
    (if mock-mailersend-enabled
      {:state [:platypub/deref mock-mailersend-state]}
      {:biff.fx/return {:status 404}}))

  (fn [_ctx {:keys [state]}]
    {:status 200, :body {:data (:emails state)}}))

(defpipeline activities
  (fn [{:keys [platypub/mock-mailersend-enabled
               platypub/mock-mailersend-state]}]
    (if mock-mailersend-enabled
      {:state [:platypub/deref mock-mailersend-state]}
      {:biff.fx/return {:status 404}}))

  (fn [_ctx {:keys [state]}]
    {:status 200
     :body   {:data  (:activities state)
              :links {:next nil}
              :meta  {:current_page 1}}}))

(def module
  {:biff.core/init
   {:platypub/mock-mailersend-state (atom {:emails [], :activities []})}

   :biff.ring/api-routes
   [[(send-email-path) {:post send-email}]
    [(send-bulk-email-path) {:post send-bulk-email}]
    [(emails-path) {:get emails}]
    [(activities-path) {:get activities}]]})
