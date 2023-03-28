(ns com.platypub.themes.common
  (:require [hiccup2.core :as hiccup]
            [hiccup.util :refer [raw-string]]
            [babashka.fs :as fs]
            [cheshire.core :as cheshire]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn safe-spit [f & args]
  (io/make-parents f)
  (apply spit f args))

(defn safe-copy [src dest]
  (when (fs/exists? src)
    (io/make-parents dest)
    (fs/copy src dest {:replace-existing true})))

(defn url-encode [s]
  (java.net.URLEncoder/encode (str s) "UTF-8"))

(defn map->query [m]
  (->> m
       (map (fn [[k v]]
              (str (url-encode (name k)) "=" (url-encode v))))
       (str/join "&")))

(def rfc3339 "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

(def interpunct " · ")

(defn cached-img-url [opts]
  (str "https://images.weserv.nl/?" (map->query opts)))

(defn format-date
  ([fmt date]
   (when date
     (.. (java.time.format.DateTimeFormatter/ofPattern fmt)
         (withLocale java.util.Locale/ENGLISH)
         (withZone (java.time.ZoneId/of "UTC"))
         (format (.toInstant date)))))
  ([date]
   (format-date rfc3339 date)))

(def emdash [:span (raw-string "&mdash;")])

(def endash [:span (raw-string "&#8211;")])

(def nbsp [:span (raw-string "&nbsp;")])

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

;; From https://realfavicongenerator.net
(def favicon-settings
  (list
   [:link {:rel "apple-touch-icon", :sizes "180x180", :href "/apple-touch-icon.png"}]
   [:link {:rel "icon", :type "image/png", :sizes "32x32", :href "/favicon-32x32.png"}]
   [:link {:rel "icon", :type "image/png", :sizes "16x16", :href "/favicon-16x16.png"}]
   [:link {:rel "manifest", :href "/site.webmanifest"}]
   [:link {:rel "mask-icon", :href "/safari-pinned-tab.svg", :color "#5bbad5"}]
   [:meta {:name "msapplication-TileColor", :content "#da532c"}]
   [:meta {:name "theme-color", :content "#ffffff"}]))

(defn base-html [{:base/keys [head path body-style] :keys [site] :as opts} & body]
  (let [[title
         description
         image
         base-url] (for [k ["title" "description" "image" "url"]]
                     (or (get opts (keyword "base" k))
                         (get-in opts [:post (keyword k)])
                         (get-in opts [:page (keyword k)])
                         (get-in opts [:site (keyword k)])))
        title (or title (:title site))]
    [:html
     {:lang "en-US"
      :style {:min-height "100%"
              :height "auto"}}
     [:head
      [:title title]
      [:meta {:charset "UTF-8"}]
      [:meta {:name "description" :content description}]
      [:meta {:content title :property "og:title"}]
      [:meta {:content description :property "og:description"}]
      (when image
        (list
          [:meta {:content "summary_large_image" :name "twitter:card"}]
          [:meta {:content image :name "twitter:image"}]
          [:meta {:content image :property "og:image"}]))
      [:meta {:content (str base-url path) :property "og:url"}]
      [:link {:ref "canonical" :href (str base-url path)}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:meta {:charset "utf-8"}]
      [:link {:href "/feed.xml",
              :title (str "Feed for " (:title site)),
              :type "application/atom+xml",
              :rel "alternate"}]
      [:link {:rel "stylesheet" :href "/css/main.css"}]
      [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]
      [:script {:src "https://www.google.com/recaptcha/api.js"
                :async "async"
                :defer "defer"}]
      favicon-settings
      (when-some [html (:embed-html site)]
        (raw-string html))
      head]
     [:body
      {:style (merge {:position "absolute"
                      :width "100%"
                      :min-height "100%"
                      :display "flex"
                      :flex-direction "column"}
                     body-style)}
      body]]))

(defn atom-feed* [{:keys [posts path site] :as opts}]
  (let [feed-url (str (:url site) path)
        posts (remove #(contains? (:tags %) "unlisted") posts)]
    [:feed {:xmlns "http://www.w3.org/2005/Atom"}
     [:title (:title site)]
     [:id feed-url]
     [:updated (format-date (:published-at (first posts)))]
     (when-some [url (not-empty (:icon site))]
       (list
        [:icon url]
        [:logo url]))
     [:link {:rel "self" :href feed-url :type "application/atom+xml"}]
     [:link {:href (:url site)}]
     (for [post (take 10 posts)
           :let [url (str (:url site) "/p/" (:slug post) "/")]]
       [:entry
        [:title {:type "html"} (:title post)]
        [:id url]
        [:updated (format-date (:published-at post))]
        [:content {:type "html"} (:html post)]
        [:link {:href url}]
        [:author
         [:name (:author-name site)]
         (when-some [url (not-empty (:author-url site))]
           [:uri url])]])]))

(defn embed-discourse [{:keys [forum-url page-url]}]
  (list
    [:div#discourse-comments]
    [:script {:type "text/javascript"}
     (raw-string
       "DiscourseEmbed = { discourseUrl: '" forum-url "',"
       "                   discourseEmbedUrl: '" page-url "' };"
       "(function() { "
       "  var d = document.createElement('script'); d.type = 'text/javascript'; d.async = true; "
       "  d.src = DiscourseEmbed.discourseUrl + 'javascripts/embed.js'; "
       "  (document.getElementsByTagName('head')[0] || document.getElementsByTagName('body')[0]).appendChild(d); "
       "})();")]))

(defn render! [path doctype hiccup]
  (let [path (str "public" (str/replace path #"/$" "/index.html"))]
    (io/make-parents path)
    (safe-spit path (str doctype "\n" (hiccup/html hiccup)))))

(defn atom-feed! [opts]
  (render! "/feed.xml"
           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
           (atom-feed* (assoc opts :path "/feed.xml"))))

(defn posts! [{:keys [posts] :as opts} render-post]
  (doseq [post posts
          :let [path (str "/p/" (:slug post) "/")]]
    (render! path
             "<!DOCTYPE html>"
             (render-post (assoc opts :base/path path :post post)))))

(defn pages! [opts render-page pages]
  (doseq [page (:pages opts)
          :let [path (str/replace (str "/" (:path page) "/")
                                  #"/+" "/")]]
    (render! path
             "<!DOCTYPE html>"
             (render-page (assoc opts :base/path path :page page))))
  (doseq [[path render-page] pages]
      (render! path
               "<!DOCTYPE html>"
               (render-page (assoc opts :base/path path)))))

(defn without-ns [m]
  (update-keys m (comp keyword name)))

(defn derive-opts [{:keys [site item lists posts pages] :as opts}]
  (let [posts (->> posts
                   (map without-ns)
                   (remove :draft)
                   (map #(update % :tags set))
                   (sort-by :published-at #(compare %2 %1)))
        pages (->> pages
                   (map without-ns)
                   (remove :draft)
                   (map #(update % :tags set)))
        welcome (->> (concat pages posts) ; include posts for backwards compatibility
                     (filter #((:tags %) "welcome"))
                     first)
        posts (remove #((:tags %) "welcome") posts)
        pages (remove #((:tags %) "welcome") pages)
        about (->> pages
                   (filter #((:tags %) "about"))
                   first)]
    (assoc opts
           :site (without-ns site)
           :post (-> item
                     without-ns
                     (update :tags set))
           :posts posts
           :pages pages
           :list (without-ns (first lists))
           :welcome welcome
           :about about)))

(defn netlify-subscribe-fn! [{:keys [db site-id site welcome account] lst :list :as opts}]
  (safe-spit
   "netlify/functions/config.json"
   (cheshire/generate-string
    {:subscribeRedirect (str (:url site) "/subscribed/")
     :listAddress (:address lst)
     :mailgunDomain (:mailgun/domain account)
     :mailgunKey (:mailgun/api-key account)
     :welcomeEmail {:from (str (:title lst)
                               " <doreply@" (:mailgun/domain account) ">")
                    :h:Reply-To (:reply-to lst)
                    :subject (:title welcome)
                    :html (:html welcome)}
     :recaptchaSecret (:recaptcha/secret account)
     :siteUrl (:url site)}))
  (safe-copy (io/resource "com/platypub/themes/common/subscribe.js")
             "netlify/functions/subscribe.js"))

(defn redirects! [{:keys [site]}]
  (safe-spit "public/_redirects" (:redirects site)))

(defn sitemap! [{:keys [exclude]}]
  (->> (file-seq (io/file "public"))
       (filter #(.isFile %))
       (map #(.getPath %))
       (filter #(str/ends-with? % "index.html"))
       (map (fn [path]
              (-> path
                  (str/replace #"^public" "")
                  (str/replace #"index.html$" ""))))
       (remove (fn [path]
                 (some #(re-matches % path) exclude)))
       (str/join "\n")
       (safe-spit "public/sitemap.txt")))
