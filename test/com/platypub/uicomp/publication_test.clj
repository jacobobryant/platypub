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
                     :publication/padding-color    "#eee"
                     :publication/background-color "#fff"
                     :publication/text-color       "#111"
                     :publication/primary-color    "#00f"}
        render      (:publication/ui-subscribe-form
                     (helpers/resolve-resolver
                      publication/subscribe-form
                      {:biff.auth/turnstile-site-key "site"}
                      publication))
        html        (chassis/html (render {}))
        preview     (chassis/html (render {:preview true}))
        skip-render (:publication/ui-subscribe-form
                     (helpers/resolve-resolver
                      publication/subscribe-form
                      {:biff.auth/skip-captcha true}
                      publication))]
    (is (fn? render))
    (is (str/includes? html "Subscribe to News"))
    (is (str/includes? html "Current stories"))
    (is (str/includes? html "cf-turnstile"))
    (is (str/includes? html "data-sitekey=\"site\""))
    (is (not (str/includes? (chassis/html (skip-render {}))
                            "cf-turnstile")))
    (is (str/includes? preview "Subscribe to News"))
    (is (str/includes? preview "disabled"))
    (is (not (str/includes? preview "cf-turnstile")))))
