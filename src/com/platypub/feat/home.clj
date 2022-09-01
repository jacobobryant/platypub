(ns com.platypub.feat.home
  (:require [com.biffweb :as biff]
            [com.platypub.ui :as ui]
            [com.platypub.util :as util]))

(defn recaptcha-disclosure [{:keys [link-class]}]
  [:span "This site is protected by reCAPTCHA and the Google "
   [:a {:href "https://policies.google.com/privacy"
        :target "_blank"
        :class link-class}
    "Privacy Policy"] " and "
   [:a {:href "https://policies.google.com/terms"
        :target "_blank"
        :class link-class}
    "Terms of Service"] " apply."])

(defn signin-form [{:keys [recaptcha/site] :as sys}]
  (biff/form
   {:id "signin-form"
    :action "/auth/send"}
   [:div [:label {:for "email"} "Email address:"]]
   [:.h-1]
   [:.flex
    [:input#email
     {:name "email"
      :type "email"
      :autocomplete "email"
      :placeholder "Enter your email address"}]
    [:.w-3]
    [:button.btn.g-recaptcha
     (merge
      (when (util/enable-recaptcha? sys)
        {:data-sitekey site
         :data-callback "onSubscribe"
         :data-action "subscribe"})
      {:type "submit"})
     "Sign in"]]
   [:.h-1]
   (if (util/enable-recaptcha? sys)
     [:.text-sm (recaptcha-disclosure {:link-class "link"})]
     [:.text-sm
      "Doesn't need to be a real address. "
      "Until you add an API key for Mailgun, we'll just print your sign-in "
      "link to the console."])))

(defn password-signup-form [{:keys [recaptcha/site] :as sys}]
  (biff/form
   {:id "password-signup-form"
    :action "/auth/signup"}
   (ui/text-input {:id "email"
                   :label "Email address:"
                   :type "email"
                   :placeholder "Enter your email address"})
   [:.h-1]
   (ui/text-input {:id "password"
                   :label "Password:"
                   :type "password"
                   :placeholder "********"})
   [:.h-1]
   (ui/text-input {:id "confirm-password"
                   :label "Confirm password:"
                   :type "password"
                   :placeholder "********"})
   [:.h-3]
   [:button.btn.g-recaptcha
    (merge
     (when (util/enable-recaptcha? sys)
       {:data-sitekey site
        :data-callback "onPasswordSignup"
        :data-action "signup"})
     {:type "submit"})
    "Sign up"]
   [:.h-1]
   (if (util/enable-recaptcha? sys)
     [:.text-sm (recaptcha-disclosure {:link-class "link"})]
     [:.text-sm
      "Doesn't need to be a real address. "
      "Until you add an API key for Mailgun, we'll just print your sign-in "
      "link to the console."])))

(defn password-signin-form [{:keys [recaptcha/site] :as sys}]
  (biff/form
   {:id "password-signin-form"
    :action "/auth/signin"}
   (ui/text-input {:id "email"
                   :label "Email address:"
                   :type "email"
                   :placeholder "Enter your email address"})
   [:.h-1]
   (ui/text-input {:id "password"
                   :label "Password:"
                   :type "password"
                   :placeholder "********"})
   [:.h-3]
   [:button.btn.g-recaptcha
    (merge
     (when (util/enable-recaptcha? sys)
       {:data-sitekey site
        :data-callback "onPasswordSignin"
        :data-action "signin"})
     {:type "submit"})
    "Sign in"]
   [:.h-1]
   (if (util/enable-recaptcha? sys)
     [:.text-sm (recaptcha-disclosure {:link-class "link"})]
     [:.text-sm
      "Doesn't need to be a real address. "
      "Until you add an API key for Mailgun, we'll just print your sign-in "
      "link to the console."])))

(def recaptcha-scripts
  [[:script {:src "https://www.google.com/recaptcha/api.js"
             :async "async"
             :defer "defer"}]
   [:script (biff/unsafe
             (str "function onSubscribe(token) { document.getElementById('signin-form').submit(); }"))]
   [:script (biff/unsafe
             (str "function onPasswordSignup(token) { document.getElementById('password-signup-form').submit(); }"))]
   [:script (biff/unsafe
             (str "function onPasswordSignin(token) { document.getElementById('password-signin-form').submit(); }"))]])


(defn home [sys]
  (ui/page
   {:base/head (when (util/enable-recaptcha? sys)
                 recaptcha-scripts)}
   (signin-form sys)
   [:.h-6]
   (password-signup-form sys)
   [:.h-6]
   (password-signin-form sys)))

(defn wrap-redirect-signed-in [handler]
  (fn [{:keys [session] :as req}]
    (if (some? (:uid session))
      {:status 303
       :headers {"location" "/app"}}
      (handler req))))

(def features
  {:routes [["" {:middleware [wrap-redirect-signed-in]}
             ["/" {:get home}]]]})
