(ns com.platypub.email
  (:require [clj-http.client :as http]
            [com.platypub.settings :as settings]
            [clojure.tools.logging :as log]
            [rum.core :as rum]))

(defn signin-link [{:keys [to url user-exists]}]
  {:to to
   :subject (if user-exists
              (str "Sign in to " settings/app-name)
              (str "Sign up for " settings/app-name))
   :html (rum/render-static-markup
          [:html
           [:body
            [:p "We received a request to sign in to " settings/app-name
             " using this email address. Click this link to sign in:"]
            [:p [:a {:href url :target "_blank"} "Click here to sign in."]]
            [:p "This link will expire in one hour. "
             "If you did not request this link, you can ignore this email."]]])
   :text (str "We received a request to sign in to " settings/app-name
              " using this email address. Click this link to sign in:\n"
              "\n"
              url "\n"
              "\n"
              "This link will expire in one hour. If you did not request this link, "
              "you can ignore this email.")})

(defn signin-code [{:keys [to code user-exists]}]
  {:to to
   :subject (if user-exists
              (str "Sign in to " settings/app-name)
              (str "Sign up for " settings/app-name))
   :html (rum/render-static-markup
          [:html
           [:body
            [:p "We received a request to sign in to " settings/app-name
             " using this email address. Enter the following code to sign in:"]
            [:p {:style {:font-size "2rem"}} code]
            [:p
             "This code will expire in three minutes. "
             "If you did not request this code, you can ignore this email."]]])
   :text (str "We received a request to sign in to " settings/app-name
                   " using this email address. Enter the following code to sign in:\n"
                   "\n"
                   code "\n"
                   "\n"
                   "This code will expire in three minutes. If you did not request this code, "
                   "you can ignore this email.")})

(defn template [k opts]
  ((case k
     :signin-link signin-link
     :signin-code signin-code)
   opts))

(defn send-mailgun [{:keys [biff/secret mailgun/domain]} form-params]
  (let [result (http/post (str "https://api.mailgun.net/v3/" domain "/messages")
                          {:basic-auth ["api" (secret :mailgun/api-key)]
                           :form-params (merge {:from (str "Platypub <noreply@" domain ">")}
                                               form-params)
                           :throw-exceptions false})
        success (< (:status result) 400)]
    (when-not success
      (log/error (:body result)))
    success))

(defn send-console [ctx form-params]
  (println "TO:" (:to form-params))
  (println "SUBJECT:" (:subject form-params))
  (println)
  (println (:text form-params))
  (println)
  (println "To send emails instead of printing them to the console, add your"
           "API keys for Mailgun and Recaptcha to config.edn.")
  true)

(defn send-email [{:keys [biff/secret recaptcha/site-key] :as ctx} opts]
  (let [form-params (if-some [template-key (:template opts)]
                      (template template-key opts)
                      opts)]
    (if (every? some? [(secret :mailgun/api-key)
                       (secret :recaptcha/secret-key)
                       site-key])
      (send-mailgun ctx form-params)
      (send-console ctx form-params))))
