(require '[hiccup2.core :as hiccup])
(require '[hiccup.util :refer [raw-string]])
(require '[babashka.curl :as curl])
(require '[babashka.fs :as fs])
(require '[selmer.parser :as selmer])

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

(defn read-edn-file [path]
  (when (fs/exists? path)
    (edn/read-string (slurp path))))

(defn custom-key [opts k]
  (or (not-empty (get-in opts [:site :site/custom-config k]))
      (get-in opts [:custom-config k])))

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

(defn base-html [{:base/keys [head path] :keys [site] :as opts} & body]
  (let [[title
         description
         image
         url] (for [k ["title" "description" "image" "url"]]
                (or (get opts (keyword "base" k))
                    (get-in opts [:post (keyword "post" k)])
                    (get-in opts [:site (keyword "site" k)])))
        title (->> [title (:site/title site)]
                   distinct
                   (str/join " | "))]
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
      [:meta {:content (str url path) :property "og:url"}]
      [:link {:ref "canonical" :href (str url path)}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:meta {:charset "utf-8"}]
      [:link {:href "/feed.xml",
              :title (str "Feed for " (:site/title site)),
              :type "application/atom+xml",
              :rel "alternate"}]
      [:link {:rel "stylesheet" :href "/css/main.css"}]
      [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]
      [:script {:src "https://www.google.com/recaptcha/api.js"
                :async "async"
                :defer "defer"}]
      (when-some [html (custom-key opts :com.platypub/embed-html)]
        (raw-string html))
      head]
     [:body
      {:style {:position "absolute"
               :width "100%"
               :min-height "100%"
               :display "flex"
               :flex-direction "column"}}
      body]]))

(defn author [{:keys [post] {:keys [site/custom-config]} :site :as opts}]
  {:name (custom-key opts :com.platypub/author-name)
   :url (custom-key opts :com.platypub/author-url)
   :image (custom-key opts :com.platypub/author-image)})

(defn atom-feed* [{{:site/keys [title description image url] :as site} :site
                   :keys [posts path]
                   :as opts}]
  (let [feed-url (str url path)
        posts (remove #(some (:post/tags %) ["unlisted"]) posts)]
    [:feed {:xmlns "http://www.w3.org/2005/Atom"}
     [:title title]
     [:id (url-encode feed-url)]
     [:updated (format-date (:post/published-at (first posts)))]
     [:link {:rel "self" :href feed-url :type "application/atom+xml"}]
     [:link {:href url}]
     (for [{:post/keys [title slug published-at html] :as post} (take 10 posts)
           :let [url (str url "/p/" slug "/")
                 author (author opts)]]
       [:entry
        [:title {:type "html"} title]
        [:id (url-encode url)]
        [:updated (format-date published-at)]
        [:content {:type "html"} html]
        [:link {:href url}]
        [:author
         [:name (:name author)]
         [:uri (:url author)]]])]))

(defn embed-discourse [{:keys [site base/path] :as opts}]
  (when-some [forum-url (custom-key opts :com.platypub/discourse-url)]
    (list
      [:div.text-xl.font-bold.mb-3 "Comments"]
      [:div#discourse-comments.mb-5]
      [:script {:type "text/javascript"}
       (raw-string
         "DiscourseEmbed = { discourseUrl: '" forum-url "',"
         "                   discourseEmbedUrl: '" (str (:site/url site) path) "' };"
         "(function() { "
         "  var d = document.createElement('script'); d.type = 'text/javascript'; d.async = true; "
         "  d.src = DiscourseEmbed.discourseUrl + 'javascripts/embed.js'; "
         "  (document.getElementsByTagName('head')[0] || document.getElementsByTagName('body')[0]).appendChild(d); "
         "})();")])))

(defn render! [path doctype hiccup]
  (let [path (str "public" (str/replace path #"/$" "/index.html"))]
    (io/make-parents path)
    (spit path (str doctype "\n" (hiccup/html hiccup)))))

(defn atom-feed! [opts]
  (render! "/feed.xml"
           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
           (atom-feed* (assoc opts :path "/feed.xml"))))

(defn tailwind! [opts]
  (when (fs/exists? "tailwind.config.js.TEMPLATE")
    (spit "tailwind.config.js" (selmer/render (slurp "tailwind.config.js.TEMPLATE")
                                              {:primary (custom-key opts :com.platypub/primary-color)
                                               :accent (custom-key opts :com.platypub/accent-color)
                                               :tertiary (custom-key opts :com.platypub/tertiary-color)})))
  (-> (shell/sh "npx" "tailwindcss"
                "-c" "tailwind.config.js"
                "-i" "tailwind.css"
                "-o" "public/css/main.css"
                "--minify")
      :err
      print))

(defn assets! []
  (->> (file-seq (io/file "assets"))
       (filter #(.isFile %))
       (run! #(io/copy % (doto (io/file "public" (subs (.getPath %) (count "assets/"))) io/make-parents)))))

(defn posts! [opts render-post posts]
  (doseq [post posts
          :let [path (str "/p/" (:post/slug post) "/")]]
    (render! path
             "<!DOCTYPE html>"
             (render-post (assoc opts :base/path path :post post)))))

(defn pages! [opts render-page pages]
  (doseq [post (:pages opts)
          :let [path (str "/" (:post/slug post) "/")]]
    (render! path
             "<!DOCTYPE html>"
             (render-page (assoc opts :base/path path :post post))))
  (doseq [[path page] pages]
      (render! path
               "<!DOCTYPE html>"
               (page (assoc opts :base/path path)))))

(defn derive-opts [{:keys [db site-id list-id post-id account] :as opts}]
  (let [site (get db site-id)
        custom-defaults (->> (read-edn-file "custom-schema.edn")
                             (map (juxt :key :default))
                             (into {}))
        posts (->> (vals db)
                   (map #(update % :post/tags set))
                   (filter (fn [post]
                             (and (= :post (:db/doc-type post))
                                  (= :published (:post/status post))
                                  ((:post/tags post) (:site/tag site)))))
                   (sort-by :post/published-at #(compare %2 %1)))
        welcome (->> posts
                     (filter #((:post/tags %) "welcome"))
                     first)
        pages (->> posts
                   (filter #((:post/tags %) "page")))
        posts (->> posts
                   (remove #(some (:post/tags %) ["welcome" "page"])))
        lst (if list-id
              (get db list-id)
              (->> (vals db)
                   (map #(update % :list/tags set))
                   (filter (fn [lst]
                             (and (= :list (:db/doc-type lst))
                                  ((:list/tags lst) (:site/tag site)))))
                   first))
        site (if site-id
               site
               (merge (->> (vals db)
                           (filter (fn [site]
                                     (and (= :site (:db/doc-type site))
                                          ((set (:list/tags lst)) (:site/tag site)))))
                           first)
                      site))
        post (get db post-id)]
    (assoc opts
           :site site
           :list lst
           :post post
           :posts posts
           :welcome welcome
           :pages pages
           :custom-defaults custom-defaults)))

(defn netlify-fn-config! [{:keys [db site-id site welcome account] lst :list :as opts}]
  (spit "netlify/functions/config.json"
        (json/generate-string
          {:subscribeRedirect (str (:site/url site) "/subscribed/")
           :listAddress (:list/address lst)
           :mailgunDomain (:mailgun/domain account)
           :mailgunKey (:mailgun/api-key account)
           :welcomeEmail {:from (str (:list/title lst)
                                     " <doreply@" (:mailgun/domain account) ">")
                          :h:Reply-To (:list/reply-to lst)
                          :subject (:post/title welcome)
                          :html (:post/html welcome)}
           :recaptchaSecret (:recaptcha/secret account)})))

(defn redirects! [{:keys [site]}]
  (spit "public/_redirects" (:site/redirects site)))

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
       (spit "public/sitemap.txt")))
