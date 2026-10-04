(ns com.platypub.uicomp.publication-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.platypub.test-helpers :as helpers]
            [com.platypub.uicomp.publication :as publication]
            [dev.onionpancakes.chassis.core :as chassis]))

(deftest subscribe-form-resolver-test
  (let [publication {:publication/id               (random-uuid)
                     :publication/title            "News"
                     :publication/description      "Current stories"
                     :publication/archive-url      "https://example.com/news"
                     :publication/padding-color    "#eee"
                     :publication/background-color "#fff"
                     :publication/text-color       "#111"
                     :publication/primary-color    "#00f"
                     :publication/feed             {:feed/url "https://example.com/feed.xml"}}
        render      (:publication/ui-subscribe-form
                     (helpers/resolve-resolver
                      publication/subscribe-form
                      {:biff.auth/turnstile-site-key "site"}
                      publication))
        html        (chassis/html (render {:embed true}))
        hosted      (chassis/html (render {}))
        preview     (chassis/html (render {:preview true}))
        skip-render (:publication/ui-subscribe-form
                     (helpers/resolve-resolver
                      publication/subscribe-form
                      {:biff.auth/skip-captcha true}
                      publication))
        no-archive  (:publication/ui-subscribe-form
                     (helpers/resolve-resolver
                      publication/subscribe-form
                      {}
                      (dissoc publication :publication/archive-url)))]
    (is (fn? render))
    (is (str/includes? html "Sign up for News"))
    (is (str/includes? html "Current stories"))
    (is (str/includes? html "cf-turnstile"))
    (is (str/includes? html "RSS feed"))
    (is (str/includes? html "Archive"))
    (is (str/includes? html "https://example.com/news"))
    (is (not (str/includes? html "<><span>")))
    (is (not (str/includes? (chassis/html (no-archive {:embed true}))
                            "Archive")))
    (is (str/includes? html "platypub:resize"))
    (is (not (str/includes? hosted "platypub:resize")))
    (is (str/includes? hosted "flex-[2]"))
    (is (str/includes? html "data-sitekey=\"site\""))
    (is (not (str/includes? html "js.hcaptcha.com/1/api.js")))
    (is (not (str/includes? html "hcaptcha-fallback")))
    (is (not (str/includes? (chassis/html (skip-render {}))
                            "cf-turnstile")))
    (is (str/includes? preview "Sign up for News"))
    (is (str/includes? preview "disabled"))
    (is (not (str/includes? preview "cf-turnstile")))))

(deftest captcha-provider-configuration-test
  (let [publication {:publication/id               (random-uuid)
                     :publication/title            "News"
                     :publication/padding-color    "#eee"
                     :publication/background-color "#fff"
                     :publication/text-color       "#111"
                     :publication/primary-color    "#00f"}
        render-html (fn [ctx]
                      (let [render
                            (:publication/ui-subscribe-form
                             (helpers/resolve-resolver
                              publication/subscribe-form ctx publication))]
                        (chassis/html (render {}))))
        hcaptcha    (render-html {:platypub/hcaptcha-site-key "h-site"})
        both        (render-html {:biff.auth/turnstile-site-key "t-site"
                                  :platypub/hcaptcha-site-key   "h-site"})
        neither     (render-html {})]
    (is (str/includes? hcaptcha "js.hcaptcha.com/1/api.js"))
    (is (str/includes? hcaptcha "data-sitekey=\"h-site\""))
    (is (re-find #"class=\"h-captcha\"" hcaptcha))
    (is (not (str/includes? hcaptcha "cf-turnstile")))
    (is (not (str/includes? hcaptcha "turnstile/v0/api.js")))
    (is (str/includes? both "turnstile/v0/api.js"))
    (is (str/includes? both "js.hcaptcha.com/1/api.js"))
    (is (re-find #"class=\"h-captcha hidden\"" both))
    (is (not (str/includes? neither "turnstile/v0/api.js")))
    (is (not (str/includes? neither "js.hcaptcha.com/1/api.js")))))

(deftest pill-subscribe-form-test
  (let [publication {:publication/id               (random-uuid)
                     :publication/title            "Jacob's Newsletter"
                     :publication/padding-color    "#eee"
                     :publication/background-color "#eee"
                     :publication/text-color       "#222"
                     :publication/primary-color    "#db2777"
                     :publication/form-style       :publication.form-style/pill
                     :publication/hide-form-title  true
                     :publication/description      "A note from Jacob"
                     :publication/form-placeholder "Enter thine email address"}
        render      (:publication/ui-subscribe-form
                     (helpers/resolve-resolver
                      publication/subscribe-form
                      {:biff.auth/skip-captcha true}
                      publication))
        html        (chassis/html (render {:embed true}))]
    (is (str/includes? html "rounded-l-full"))
    (is (str/includes? html "rounded-r-full"))
    (is (str/includes? html "Enter thine email address"))
    (is (str/includes? html "A note from Jacob"))
    (is (not (str/includes? html "Sign up for")))))
