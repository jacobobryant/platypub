(ns com.platypub.util
  (:require [lambdaisland.hiccup :as h]
            [babashka.fs :as fs]
            [cheshire.core :as cheshire]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-yaml.core :as yaml])
  (:import [org.commonmark.node Node]
           [org.commonmark.parser Parser]
           [org.commonmark.renderer.html HtmlRenderer]
           [org.commonmark.ext.heading.anchor HeadingAnchorExtension]
           [org.commonmark.ext.gfm.strikethrough StrikethroughExtension]
           [org.commonmark.ext.image.attributes ImageAttributesExtension]))

(let [extensions [(HeadingAnchorExtension/create)
                  (StrikethroughExtension/create)
                  (ImageAttributesExtension/create)]
      parser (.. (Parser/builder)
                 (extensions extensions)
                 (build))
      renderer (.. (HtmlRenderer/builder)
                   (extensions extensions)
                   (build))]
  (defn md-to-html [md]
    (.render renderer (.parse parser md))))

(defn safe-spit [f & args]
  (io/make-parents f)
  (apply spit f args))

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
  [date fmt timezone]
  (.. (java.time.format.DateTimeFormatter/ofPattern fmt)
        (withLocale java.util.Locale/ENGLISH)
        (withZone (java.time.ZoneId/of timezone))
        (format (.toInstant date))))

(defn parse-date [date-str fmt timezone]
  (.parse (doto (java.text.SimpleDateFormat. fmt)
            (.setTimeZone (java.util.TimeZone/getTimeZone timezone))) date-str))

(def emdash [:span [::h/unsafe-html "&mdash;"]])

(def endash [:span [::h/unsafe-html "&#8211;"]])

(def nbsp [:span [::h/unsafe-html "&nbsp;"]])

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

(defn base-html [{:keys [dev base/path base/head] :as opts} & body]
  (let [[title
         description
         image
         base-url] (for [k ["title" "description" "image" "url"]]
                     (or (get opts (keyword "base" k))
                         (get-in opts [:post (keyword k)])
                         (get-in opts [:page (keyword k)])
                         (get opts (keyword "site" k))))]
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
              :title (str "Feed for " (:site/title opts)),
              :type "application/atom+xml",
              :rel "alternate"}]
      favicon-settings
      head
      (when dev
        [:script {:src "/js/live.js"}])
      [:link {:rel "stylesheet" :href "/css/main.css"}]]
     [:body
      {:style {:position "absolute"
               :width "100%"
               :min-height "100%"
               :display "flex"
               :flex-direction "column"}}
      body
      (when-some [html (:site/embed-html opts)]
        [::h/unsafe-html html])]]))

(defn atom-feed* [{:keys [posts
                          path
                          site/author-name
                          site/icon
                          site/author-name
                          site/author-url]
                   site-url :site/url
                   site-title :site/title}]
  (let [feed-url (str site-url path)
        posts (remove (comp (some-fn :unlisted :nofeed) :tags) posts)]
    [:feed {:xmlns "http://www.w3.org/2005/Atom"}
     [:title site-title]
     [:id feed-url]
     [:updated (format-date (:published (first posts)) rfc3339 "UTC")]
     (when (not-empty icon)
       (list [:icon icon]
             [:logo icon]))
     [:link {:rel "self" :href feed-url :type "application/atom+xml"}]
     [:link {:href site-url}]
     (for [{:keys [slug published html]
            post-title :title} (take 10 posts)
           :let [post-url (str site-url "/p/" slug "/")]]
       [:entry
        [:title {:type "html"} post-title]
        [:id post-url]
        [:updated (format-date published rfc3339 "UTC")]
        [:content {:type "html"} html]
        [:link {:href post-url}]
        [:author
         [:name author-name]
         (when (not-empty author-url)
           [:uri author-url])]])]))

(defn render! [path hiccup & [doctype]]
  (safe-spit (io/file "public" (-> path
                                   (str/replace #"/$" "/index.html")
                                   (str/replace #"^/" "")))
             (if doctype
               (str doctype "\n" (h/render hiccup {:doctype? false}))
               (h/render hiccup))))

(defn atom-feed! [opts]
  (render! "/feed.xml" (atom-feed* (assoc opts :path "/feed.xml")) "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"))

(defn custom-pages! [opts pages]
  (doseq [[path render-fn] pages]
    (render! path (render-fn (assoc opts :base/path path)))))

(defn posts! [{:keys [posts] :as opts} render-fn]
  (doseq [post posts
          :let [path (str "/p/" (:slug post) "/")]]
    (render! path (render-fn (assoc opts :base/path path :post post)))))

(defn cards! [{:keys [posts] :as opts} render-fn]
  (doseq [post posts
          :let [path (str "/p/" (:slug post) "/card/")]]
    (render! path (render-fn (assoc opts :base/path path :post post)))))

(defn emails! [{:keys [posts] :as opts} render-fn]
  (doseq [post posts
          :let [path (str "/p/" (:slug post) "/email/")]]
    (render! path (render-fn (assoc opts :post post)))))

(defn pages! [{:keys [pages] :as opts} render-fn]
  (doseq [page (:pages opts)
          :let [path (-> (:path page)
                         (str/replace #"^pages/" "")
                         (str/replace #"\.md$" "")
                         (#(str "/" % "/"))
                         (str/replace #"/+" "/"))]]
    (render! path (render-fn (assoc opts :base/path path :page page)))))

(defn netlify-subscribe-fn! [{:keys [site/url
                                     list/address
                                     list/title
                                     list/reply-to
                                     mailgun/domain
                                     mailgun/api-key
                                     recaptcha/secret-key
                                     emails]}]
  (let [welcome-email (first (filter (comp #{"emails/welcome.md"} :path) emails))]
    (safe-spit (io/file "netlify/functions/config.json")
               (cheshire/generate-string
                {:subscribeRedirect (str url "/subscribed/")
                 :listAddress address
                 :mailgunDomain domain
                 :mailgunKey (api-key)
                 :welcomeEmail {:from (str title " <doreply@" domain ">")
                                :h:Reply-To reply-to
                                :subject (:subject welcome-email)
                                :html (:html welcome-email)}
                 :recaptchaSecret (secret-key)
                 :siteUrl url}))
    (io/copy (io/file (io/resource "com/platypub/subscribe.js"))
             (doto (io/file "netlify/functions/subscribe.js") io/make-parents))))

(defn redirects! [{:keys [site/redirects]}]
  (safe-spit (io/file "public/_redirects")
             (->> redirects (mapv #(str/join " " %)) (str/join "\n"))))

(defn sitemap! [_]
  (let [root (io/file "public")]
    (->> (file-seq root)
         (filterv #(.isFile %))
         (mapv #(.getPath %))
         (filterv #(str/ends-with? % "index.html"))
         (mapv (fn [path]
                 (-> path
                     (subs (count (.getPath root)))
                     (str/replace #"index.html$" ""))))
         (str/join "\n")
         (safe-spit (io/file "public/sitemap.txt")))))

(defn read-md-file [{:keys [site/timezone]} f]
  (let [root (io/file "content")
        content (slurp f)
        [front-matter content] (->> (str/split content #"---" 3)
                                    (keep (comp not-empty str/trim)))
        {:keys [tags published content-type]
         :or {content-type "markdown"}
         :as front-matter} (some-> front-matter yaml/parse-string)
        html (case content-type
               "markdown" (some-> content md-to-html)
               "html" content)
        path (subs (.getPath f) (inc (count (.getPath root))))
        doc-type (keyword (first (str/split path #"/" 2)))]
    (merge {:path path
            :doc-type doc-type
            :html html}
           front-matter
           (when (not-empty tags)
             {:tags (into #{} (map keyword tags))})
           (when (some? published)
             {:published (parse-date published "yyyy-MM-dd'T'HH:mm:ss a" timezone)}))))

(defn read-content [config]
  (let [root (io/file "content")]
    (->> (file-seq root)
         (filterv #(.isFile %))
         (mapv #(read-md-file config %))
         (remove :draft)
         (sort-by :published #(compare %2 %1))
         (group-by :doc-type))))

(defn render-default! [{::keys [custom-pages
                                render-post
                                render-page
                                render-card
                                render-email]
                        :keys [dev]
                        :as ctx}]
  (when dev
    (io/copy (io/file "resources/com/platypub/live.js") (doto (io/file "public/js/live.js") io/make-parents))
    (cards! ctx render-card)
    (emails! ctx render-email))
  (fs/copy-tree (io/file "resources/public") (io/file "public") {:replace-existing true})
  (custom-pages! ctx custom-pages)
  (posts! ctx render-post)
  (pages! ctx render-page)
  (redirects! ctx)
  (netlify-subscribe-fn! ctx)
  (atom-feed! ctx)
  (sitemap! ctx))

;; Algorithm adapted from dotenv-java:
;; https://github.com/cdimascio/dotenv-java/blob/master/src/main/java/io/github/cdimascio/dotenv/internal/DotenvParser.java
;; Wouldn't hurt to take a more thorough look at Ruby dotenv's algorithm:
;; https://github.com/bkeepers/dotenv/blob/master/lib/dotenv/parser.rb
(defn parse-env-var [line]
  (let [line (str/trim line)
        [_ _ k v] (re-matches #"^\s*(export\s+)?([\w.\-]+)\s*=\s*(['][^']*[']|[\"][^\"]*[\"]|[^#]*)?\s*(#.*)?$"
                              line)]
    (when-not (or (str/starts-with? line "#")
                  (str/starts-with? line "////")
                  (empty? v))
      (let [v (str/trim v)
            v (if (or (re-matches #"^\".*\"$" v)
                      (re-matches #"^'.*'$" v))
                (subs v 1 (dec (count v)))
                v)]
        [k v]))))

(defn get-env []
  (reduce into
          {}
          [(some->> (try (slurp "config.env") (catch Exception _))
                    str/split-lines
                    (keep parse-env-var))
           (System/getenv)]))

(defn read-config []
  (let [env (get-env)]
    (edn/read-string {:readers {'secret (fn [env-var]
                                          (constantly (get env (str env-var))))}}
                     (slurp "resources/config.edn"))))
