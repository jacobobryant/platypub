(ns com.platypub.lib.email
  (:require [com.biffweb.fx :refer [defpipeline]]))

(def unsubscribe-placeholder "{{unsubscribe_url}}")

(defn request
  [{:mailersend/keys [api-key base-url from from-name reply-to plan]}
   {:keys [to subject html text unsubscribe-url] :as message}]
  (let [from-name (or (:from-name message) from-name)
        reply-to  (or (:reply-to message) reply-to)]
    {:method           :post
     :url              (str (or base-url "https://api.mailersend.com")
                            "/v1/email")
     :headers          (cond-> {"Authorization" (str "Bearer " (force api-key))}
                         (and unsubscribe-url (= plan :professional))
                         (assoc "List-Unsubscribe" (str "<" unsubscribe-url ">")
                                "List-Unsubscribe-Post"
                                "List-Unsubscribe=One-Click"))
     :content-type     :json
     :throw-exceptions false
     :as               :json
     :form-params      {:from     {:email from, :name from-name}
                        :reply_to {:email reply-to, :name from-name}
                        :to       [{:email to}]
                        :subject  subject
                        :html     html
                        :text     text}}))

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
