(ns com.platypub.uicomp.publication
  (:require [com.biffweb.datastar :as datastar]
            [com.biffweb.graph :refer [defresolver]]
            [com.platypub.routes :as routes]))

(defresolver subscribe-form
  {:input  [:publication/id
            :publication/title
            [:? :publication/description]
            [:? :publication/hide-form-title]
            [:? :publication/archive-url]
            [:? :publication/form-placeholder]
            [:? :publication/form-style]
            :publication/padding-color
            :publication/background-color
            :publication/text-color
            :publication/primary-color
            {:publication/feed [:feed/url]}]
   :output [:publication/ui-subscribe-form]}
  [ctx publication]
  {:publication/ui-subscribe-form
   (fn [{:keys [preview embed]}]
     [:main
      {:style (str "background:" (:publication/padding-color publication)
                   ";color:" (:publication/text-color publication))
       :class (concat
               ["w-full text-center"]
               (cond
                 (not embed)
                 ["flex min-h-screen flex-col px-3"]

                 (= :publication.form-style/pill
                    (:publication/form-style publication))
                 ["px-4 text-base"]

                 :else
                 ["px-3 pb-12 pt-5"]))}
      (when-not embed [:div {:class ["flex-1"]}])
      [:div
       {:class (cond-> ["mx-auto w-full max-w-md"]
                 (not= (:publication/background-color publication)
                       (:publication/padding-color publication))
                 (conj "rounded p-4"))
        :style (str "background:"
                    (:publication/background-color publication))}
       [:div {:data-show "!$subscription_submitted"}
        (when-not (:publication/hide-form-title publication)
          [:h1 {:class ["text-lg font-bold"]}
           (str "Sign up for " (:publication/title publication))])
        (when-let [description (:publication/description publication)]
          [:p description])
        [:div {:class ["h-5"]}]]
       (when-not preview
         [:div {:data-show "$subscription_submitted" :style "display:none"}
          [:h2 {:class ["text-lg font-bold"]} "Check your inbox"]
          [:p
           "We've sent a confirmation email to "
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
        [:div {:class (if (= :publication.form-style/pill
                             (:publication/form-style publication))
                        ["flex"]
                        ["flex flex-col gap-2 sm:flex-row"])}
         [:input
          (cond-> {:type        "email"
                   :placeholder (or (:publication/form-placeholder publication)
                                    "Enter your email")
                   :aria-label  "Email address"
                   :class       (if (= :publication.form-style/pill
                                       (:publication/form-style publication))
                                  ["min-w-0 flex-1 rounded-l-full border"
                                   "border-r-0 bg-white px-3 py-2 text-base"
                                   "text-black focus:outline-none focus:ring-0"]
                                  ["min-w-0 flex-1 rounded border"
                                   "border-stone-300"
                                   "bg-white px-3 py-2 text-black shadow"
                                   "focus:border-indigo-700"
                                   "focus:ring-indigo-700"])}
            preview (assoc :disabled true)
            (not preview) (assoc :data-bind
                                 (datastar/signal-name :subscription/email)
                                 :required true))]
         [:button
          {:type  (if preview "button" "submit")
           :style (str "background:" (:publication/primary-color publication))
           :class (if (= :publication.form-style/pill
                         (:publication/form-style publication))
                    ["rounded-r-full px-4 py-2 font-bold text-white"
                     "hover:opacity-75"]
                    ["rounded px-4 py-2 text-white shadow"])}
          "Subscribe"]]
        (when (and (not preview) (not (:biff.auth/skip-captcha ctx)))
          [:div
           [:div {:class         "cf-turnstile"
                  :data-sitekey  (:biff.auth/turnstile-site-key ctx)
                  :data-callback "platypubTurnstile"}]
           [:div {:id            "hcaptcha-fallback"
                  :class         "h-captcha hidden"
                  :data-sitekey  (:platypub/hcaptcha-site-key ctx)
                  :data-callback "platypubHcaptcha"}]
           [:input {:id        "turnstile-token"          :type "hidden"
                    :data-bind (datastar/signal-name
                                :request/turnstile-token)}]
           [:input {:id        "hcaptcha-token"          :type "hidden"
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
           [:script
            {:src   "https://js.hcaptcha.com/1/api.js"
             :async true
             :defer true}]])
        (when (or (get-in publication [:publication/feed :feed/url])
                  (:publication/archive-url publication))
          [:div {:class ["text-center"]}
           (when-let [feed-url (get-in publication
                                       [:publication/feed :feed/url])]
             [:a {:href   feed-url
                  :target "_blank"
                  :rel    "noopener noreferrer"
                  :class  ["underline hover:opacity-75"]}
              "RSS feed"])
           (when (and (:publication/archive-url publication)
                      (get-in publication [:publication/feed :feed/url]))
             [:span " · "])
           (when-let [archive-url (:publication/archive-url publication)]
             [:a {:href   archive-url
                  :target "_top"
                  :rel    "noopener noreferrer"
                  :class  ["underline hover:opacity-75"]}
              "Archive"])])]]
      (when-not embed [:div {:class ["flex-[2]"]}])
      (when (and embed (not preview))
        [:script
         (str "(function(){"
              "function sendHeight(){parent.postMessage({"
              "type:'platypub:resize',height:Math.ceil("
              "document.querySelector('main').getBoundingClientRect().height)},"
              "'*');}"
              "new ResizeObserver(sendHeight).observe("
              "document.querySelector('main'));"
              "window.addEventListener('load',sendHeight);sendHeight();"
              "})();")])])})

(def module
  {:biff.graph/resolvers [subscribe-form]})
