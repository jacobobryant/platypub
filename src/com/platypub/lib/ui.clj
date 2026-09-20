(ns com.platypub.lib.ui
  (:require [clojure.java.io :as io]
            [dev.onionpancakes.chassis.core :as chassis]
            [com.biffweb.datastar :as biff.datastar]
            [com.platypub.routes :as routes]
            [ring.util.response :as ring-response]))

(def default-page-opts
  {:ui/title       "Platypub",
   :ui/description nil,
   :ui/lang        "en",
   :ui/image       nil,
   :ui/icon        nil})

(def ^:private datastar-script-url
  "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.1/bundles/datastar.js")

(defn- static-path
  [path]
  (if-some [last-modified
            (some-> (io/resource (str "public" path))
                    ring-response/resource-data
                    :last-modified
                    (.getTime))]
    (str path "?t=" last-modified)
    path))

(defn- html-response
  [body]
  {:status  200,
   :headers {"Content-Type" "text/html; charset=utf-8"},
   :body    (chassis/html body)})

(defn page
  [opts & contents]
  (let [{:ui/keys [title
                   description
                   lang
                   image
                   icon
                   init-datastar]}
        (merge default-page-opts opts)]
    (html-response
     [chassis/doctype-html5
      [:html
       {:lang lang, :class ["min-h-full h-auto"]}
       [:head
        [:meta {:charset "utf-8"}]
        [:meta
         {:name    "viewport",
          :content "width=device-width, initial-scale=1"}]
        (when title
          [[:title title]
           [:meta {:content title, :property "og:title"}]])
        (when description
          [[:meta
            {:name "description", :content description}]
           [:meta
            {:property "og:description",
             :content  description}]])
        (when image
          [[:meta {:content image, :property "og:image"}]
           [:meta
            {:content "summary_large_image",
             :name    "twitter:card"}]])
        (when icon
          [:link
           {:rel   "icon",
            :type  "image/png",
            :sizes "16x16",
            :href  icon}])
        [:link
         {:rel  "stylesheet",
          :href (static-path "/css/main.css")}]
        [:script {:src (static-path "/js/main.js")}]
        [:script
         {:type "module", :src datastar-script-url}]]
       [:body
        (merge
         {:class
          ["absolute min-h-full w-full flex flex-col"]}
         (when init-datastar (biff.datastar/init-opts)))
        contents]]])))

(defn app-page
  [{:keys [biff.datastar/sse-request], :as ctx} & content]
  (let [content* [:div#biff-datastar-content
                  {:class
                   ["flex min-h-full flex-1 flex-col"]}
                  content]]
    (if sse-request
      (html-response content*)
      (page (assoc ctx :ui/init-datastar true) content*))))

(defn app-shell
  [request & body]
  (apply app-page
         request
         [:header
          {:class ["border-b bg-white"]}
          [:nav
           {:class ["mx-auto flex max-w-5xl items-center"
                    "justify-between p-4"]}
           [:a {:href (routes/app), :class ["text-xl font-bold"]} "Platypub"]
           [:div
            {:class ["flex gap-4"]}
            (when (= :user.tier/admin
                     (get-in request [:platypub/user :user/tier]))
              [:a
               {:href (routes/app-admin), :class ["text-sm text-blue-700"]}
               "Admin"])
            [:form
             {:data-on:submit "@post(el.dataset.action)",
              :data-action    (routes/signout)}
             [:button {:class ["text-sm text-blue-700"]} "Sign out"]]]]]
         body))
