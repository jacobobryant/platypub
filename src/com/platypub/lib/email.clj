(ns com.platypub.lib.email
  (:require [com.biffweb.fx :refer [defpipeline]]))

(def unsubscribe-placeholder "{{unsubscribe_url}}")

(defn message
  [{:mailersend/keys [from from-name reply-to plan]}
   {:keys [to subject html text unsubscribe-url] :as message}]
  (let [from-name                 (or (:from-name message) from-name)
        reply-to                  (or (:reply-to message) reply-to)
        supports-list-unsubscribe (#{:professional :enterprise} plan)]
    (cond-> {:from     {:email from, :name from-name}
             :reply_to {:email reply-to, :name from-name}
             :to       [{:email to}]
             :subject  subject
             :html     html
             :text     text}
      unsubscribe-url
      (assoc :personalization
             [{:email to
               :data  {:unsubscribe_url unsubscribe-url}}])
      (and unsubscribe-url supports-list-unsubscribe)
      (assoc :list_unsubscribe unsubscribe-url
             :headers
             [{:name  "List-Unsubscribe-Post"
               :value "List-Unsubscribe=One-Click"}]))))

(defn request
  [{:mailersend/keys [api-key base-url] :as ctx} email]
  {:method           :post
   :url              (str (or base-url "https://api.mailersend.com")
                          "/v1/email")
   :headers          {"Authorization" (str "Bearer " (force api-key))}
   :content-type     :json
   :throw-exceptions false
   :as               :json
   :form-params      (message ctx email)})

(defn bulk-request
  [{:mailersend/keys [api-key base-url] :as ctx} emails]
  {:method           :post
   :url              (str (or base-url "https://api.mailersend.com")
                          "/v1/bulk-email")
   :headers          {"Authorization" (str "Bearer " (force api-key))}
   :content-type     :json
   :throw-exceptions false
   :as               :json
   :form-params      (mapv #(message ctx %) emails)})

(defn- success?
  [response]
  (and (:status response) (< (:status response) 400)))

(defpipeline send-email
  (fn [{:keys [mailersend/api-key] :as ctx} message]
    (if api-key
      [:biff.fx/http (request ctx message)]
      true))

  (fn [_ctx response]
    (if (map? response) (success? response) response)))
