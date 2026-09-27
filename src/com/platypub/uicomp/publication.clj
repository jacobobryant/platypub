(ns com.platypub.uicomp.publication
  (:require [com.biffweb.datastar :as datastar]
            [com.biffweb.graph :refer [defresolver]]
            [com.platypub.routes :as routes]))

(defresolver subscribe-form
  {:input [:publication/id
           :publication/title
           [:? :publication/description]
           :publication/padding-color
           :publication/background-color
           :publication/text-color
           :publication/primary-color]
   :output [:publication/ui-subscribe-form]}
  [_ctx publication]
  {:publication/ui-subscribe-form
   (fn [{:keys [request preview]}]
    [:main
     {:style (str "background:" (:publication/padding-color publication))
      :class ["flex min-h-screen items-center justify-center p-6"]}
     [:div
      {:style (str "background:" (:publication/background-color publication)
                   ";color:" (:publication/text-color publication))
       :class ["w-full max-w-xl rounded p-8"]}
      [:div {:data-show "!$subscription_submitted"}
       [:h1 {:class ["text-3xl font-bold"]}
        (str "Subscribe to " (:publication/title publication))]
       [:p {:class ["my-3"]} (:publication/description publication)]]
      (when-not preview
        [:div {:data-show "$subscription_submitted" :style "display:none"}
         [:h2 {:class ["text-2xl font-bold"]} "Check your inbox"]
         [:p {:class ["mt-3"]}
          "We've sent you a confirmation email to "
          [:strong {:data-text "$subscription_email"}] "."]])
      [:form
       (cond-> {:data-show "!$subscription_submitted"
                :class     ["grid gap-3"]}
         (not preview)
         (assoc :data-on:submit "@post(el.dataset.action)"
                :data-action (routes/subscribe (:publication/id publication))
                :data-signals__ifmissing
                (datastar/signals-json
                 {:subscription/email      ""
                  :subscription/submitted  false
                  :request/turnstile-token ""
                  :request/hcaptcha-token  ""})))
       [:div {:class ["flex gap-2"]}
        [:input
         (cond-> {:type        "email"
                  :placeholder "you@example.com"
                  :class       ["min-w-0 flex-1 rounded border p-3 text-black"]}
           preview (assoc :disabled true)
           (not preview) (assoc :data-bind
                                (datastar/signal-name :subscription/email)
                                :required true))]
        [:button
         {:type  (if preview "button" "submit")
          :style (str "background:" (:publication/primary-color publication))
          :class ["rounded px-5 py-3 text-white"]}
         "Subscribe"]]
       (when (and (not preview) (not (:biff.auth/skip-captcha request)))
         [:div
          [:div {:class         "cf-turnstile"
                 :data-sitekey  (:biff.auth/turnstile-site-key request)
                 :data-callback "platypubTurnstile"}]
          [:div {:id            "hcaptcha-fallback"
                 :class         "h-captcha hidden"
                 :data-sitekey  (:platypub/hcaptcha-site-key request)
                 :data-callback "platypubHcaptcha"}]
          [:input {:id        "turnstile-token"          :type "hidden"
                   :data-bind (datastar/signal-name
                               :request/turnstile-token)}]
          [:input {:id        "hcaptcha-token"           :type "hidden"
                   :data-bind (datastar/signal-name
                               :request/hcaptcha-token)}]
          [:script
           (str "window.platypubCaptchaToken=function(id,token){"
                "var input=document.getElementById(id);input.value=token;"
                "input.dispatchEvent(new Event('input',{bubbles:true}));};"
                "window.platypubTurnstile=function(token){"
                "platypubCaptchaToken('turnstile-token',token);};"
                "window.platypubHcaptcha=function(token){"
                "platypubCaptchaToken('hcaptcha-token',token);};")]
          [:script
           {:src     "https://challenges.cloudflare.com/turnstile/v0/api.js"
            :async   true
            :defer   true
            :onerror (str "document.getElementById('hcaptcha-fallback')"
                          ".classList.remove('hidden')")}]
          [:script {:src   "https://js.hcaptcha.com/1/api.js"
                    :async true                               :defer true}]])]]])})

(def module
  {:biff.graph/resolvers [subscribe-form]})
