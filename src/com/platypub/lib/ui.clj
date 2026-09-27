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
  (str "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.1/"
       "bundles/datastar.js"))

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
        (when (= "1" (get-in opts [:query-params "debug"]))
          [[:script
            {:src "https://cdn.jsdelivr.net/npm/eruda@3.4.3/eruda.js"}]
           [:script "eruda.init();"]])
        [:script
         {:type "module", :src datastar-script-url}]]
       [:body
        (merge
         {:class ["absolute min-h-full w-full flex flex-col"]}
         (when init-datastar (biff.datastar/init-opts)))
        contents]]])))

(defn app-page
  [{:keys [biff.datastar/sse-request], :as ctx} & content]
  (let [content* [:div#biff-datastar-content
                  {:class ["flex min-h-full flex-1 flex-col"]}
                  content]]
    (if sse-request
      (html-response content*)
      (page (assoc ctx :ui/init-datastar true) content*))))

(defn dialog-state-attrs
  [open-expression close-expression]
  {:data-effect
   (str "if (" open-expression " && !el.open) el.showModal(); "
        "else if (!(" open-expression ") && el.open) el.close()")

   :data-on:close close-expression})

(defn- app-navigation
  [request]
  [:nav {:class ["grid gap-3"]}
   [:a {:href (routes/app), :class ["text-primary hover:underline"]}
    "Publications"]
   (when (= :user.tier/admin
            (get-in request [:platypub/user :user/tier]))
     [:a {:href  (routes/app-admin)
          :class ["text-primary hover:underline"]}
      "Admin"])
   [:form
    {:data-on:submit "@post(el.dataset.action)"
     :data-action    (routes/signout)}
    [:button {:class ["text-primary hover:underline"]} "Sign out"]]])

(defn app-shell
  [request & body]
  (app-page
   request
   [:header
    {:class ["border-b border-border bg-surface"]}
    [:nav
     {:class ["flex items-center justify-between px-5 py-4"]}
     [:a {:href (routes/app), :class ["text-xl font-bold text-text"]}
      "Platypub"]
     [:button
      {:type          "button"
       :aria-label    "Open menu"
       :aria-haspopup "dialog"
       :aria-controls "mobile-navigation"
       :data-on:click "document.getElementById('mobile-navigation').showModal()"

       :class
       ["rounded border border-border px-3 py-2 lg:hidden"]}
      "☰"]]]
   [:dialog#mobile-navigation
    {:aria-label "Navigation"
     :class      ["m-0 h-dvh max-h-dvh w-64 max-w-full border-r border-border"
                  "bg-surface p-5 shadow-xl lg:hidden"]}
    [:button
     {:type          "button"
      :aria-label    "Close menu"
      :data-on:click "el.closest('dialog').close()"
      :class         ["mb-5 rounded border border-border px-3 py-2"]}
     "Close"]
    (app-navigation request)]
   [:div {:class ["flex min-h-0 flex-1 bg-background text-text"]}
    [:aside
     {:class ["hidden w-64 shrink-0 border-r border-border bg-surface p-5"
              "lg:block"]}
     (app-navigation request)]
    [:div {:class ["min-w-0 flex-1"]} body]]))

(defn publication-header
  [publication active-tab]
  (let [publication-id (:publication/id publication)]
    [:header
     [:a {:href (routes/app), :class ["text-primary hover:underline"]}
      "← Publications"]
     [:h1 {:class ["mt-4 text-3xl font-bold"]}
      (:publication/title publication)]
     [:nav {:aria-label "Publication"
            :class      ["mt-4 flex gap-5 border-b border-border"]}
      (for [[tab label path]
            [[:posts "Posts" (routes/publication publication-id)]
             [:subscribers "Subscribers"
              (routes/publication-subscribers publication-id)]
             [:settings
              "Settings"
              (routes/publication-settings publication-id)]]]
        [:a {:href  path
             :class ["border-b-2 px-1 py-3"
                     (if (= active-tab tab)
                       "border-primary font-semibold text-primary"
                       "border-transparent text-muted hover:text-text")]}
         label])]]))

(defn timestamp
  [instant]
  (when instant
    [:time
     {:datetime  (str instant)
      :data-text (str "window.platypubTimestamp('" instant "')")}
     (str instant)]))
