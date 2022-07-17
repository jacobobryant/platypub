(ns com.platypub.feat.auth
  (:require [com.biffweb :as biff]
            [com.platypub.ui :as ui]
            [com.platypub.mailgun :as mailgun]
            [com.platypub.util :as util]
            [clj-http.client :as http]
            [rum.core :as rum]
            [xtdb.api :as xt]))

;; You can enable recaptcha to prevent bot signups.
(defn human? [{:keys [params] :as sys}]
  (if-not (util/enable-recaptcha? sys)
    true
    (let [{:keys [success score]}
          (:body
           (http/post "https://www.google.com/recaptcha/api/siteverify"
                      {:form-params {:secret (util/get-secret sys :recaptcha/secret)
                                     :response (:g-recaptcha-response params)}
                       :as :json}))]
      (and success (<= 0.5 (or score 1))))))

;; You can call out to an email verification API here to block spammy/high risk
;; addresses.
(defn email-valid? [req email]
  (boolean (some->> email (re-matches #".+@.+\..+"))))

(defn signin-template [{:keys [mailgun/domain]}
                       {:keys [to url]}]
  {:from (str "Platypub <noreply@" domain ">")
   :to to
   :subject "Sign in to Platypub"
   :html (rum/render-static-markup
          [:div
           [:p "We received a request to sign in to Platypub using this email address."]
           [:p [:a {:href url :target "_blank"} "Click here to sign in."]]
           [:p "If you did not request this link, you can ignore this email."]])})

(defn send-token [{:keys [biff/base-url
                          com.platypub/enable-email-signin
                          com.platypub/allowed-users
                          anti-forgery-token
                          params] :as req}]
  (let [email (biff/normalize-email (:email params))
        token (biff/jwt-encrypt
               {:intent "signin"
                :email email
                :state (biff/sha256 anti-forgery-token)
                :exp-in (* 60 60)}
               (util/get-secret req :biff/jwt-secret))
        url (str base-url "/auth/verify/" token)
        send-link! (fn []
                     (and (human? req)
                          (or (nil? allowed-users)
                              (contains? allowed-users email))
                          (email-valid? req email)
                          (mailgun/send! req (signin-template req {:to email :url url}))))]
    (if-not (and enable-email-signin (util/get-secret req :mailgun/api-key))
      (do
        (println (str "Click here to sign in as " email ": " url))
        {:headers {"location" "/auth/sent/"}
         :status 303})
      {:status 303
       :headers {"location" (if (send-link!)
                              "/auth/sent/"
                              "/auth/fail/")}})))

(defn verify-token [{:keys [biff.xtdb/node
                            path-params
                            session
                            anti-forgery-token] :as req}]
  (let [{:keys [intent email state]} (biff/jwt-decrypt (:token path-params)
                                                       (util/get-secret req :biff/jwt-secret))
        success (and (= intent "signin")
                     (= state (biff/sha256 anti-forgery-token)))
        get-user-id #(biff/lookup-id (xt/db node) :user/email email)
        existing-user-id (when success (get-user-id))]
    (when (and success (not existing-user-id))
      (biff/submit-tx req
        [{:db/op :merge
          :db/doc-type :user
          :xt/id [:db/lookup {:user/email email}]}]))
    (if-not success
      {:status 303
       :headers {"location" "/auth/fail/"}}
      {:status 303
       :headers {"location" "/app"}
       :session (assoc session :uid (or existing-user-id (get-user-id)))})))

(defn signout [{:keys [session]}]
  {:status 303
   :headers {"location" "/"}
   :session (dissoc session :uid)})

(def signin-sent
  (ui/page
   {}
   [:div
    "The sign-in link was printed to the console. If you add an API "
    "key for Mailgun and set " [:code ":com.platypub/enable-email-signin true"]
    ", the link will be emailed to you instead."]))

(def signin-fail
  (ui/page
   {}
   [:div
    "Your sign-in request failed. There are several possible reasons:"]
   [:ul
    [:li "You opened the sign-in link on a different device or browser than the one you requested it on."]
    [:li "We're not sure you're a human."]
    [:li "We think your email address is invalid or high risk."]
    [:li "We tried to email the link to you, but there was an unexpected error."]]))

(def features
  {:routes [["/auth/send"          {:post send-token}]
            ["/auth/verify/:token" {:get verify-token}]
            ["/auth/signout"       {:post signout}]]
   :static {"/auth/sent/" signin-sent
            "/auth/fail/" signin-fail}})
