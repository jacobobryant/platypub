(ns com.platypub.themes.default
  (:require [babashka.fs :as fs]
            [cheshire.core :as cheshire]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [rum.core :as rum]
            [lambdaisland.uri :as uri]))

;;;; UTILITIES =================================================================

(defn url-encode [s]
  (java.net.URLEncoder/encode (str s) "UTF-8"))

(defn query-encode [s]
  (some-> s
          (java.net.URLEncoder/encode "UTF-8")
          (str/replace "+" "%20")))

(defn map->query [m]
  (->> m
       (map (fn [[k v]]
              (str (url-encode (name k)) "=" (url-encode v))))
       (str/join "&")))

(def rfc3339 "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

(defn format-date
  ([date fmt]
   (when date
     (.. (java.time.format.DateTimeFormatter/ofPattern fmt)
         (withLocale java.util.Locale/ENGLISH)
         (withZone (java.time.ZoneId/of "UTC"))
         (format (.toInstant date)))))
  ([date]
   (format-date date rfc3339)))

(defn parse-date [date & [format]]
  (.parse (new java.text.SimpleDateFormat (or format rfc3339)) date))

(defn crop-date [d fmt]
  (-> d
      (format-date fmt)
      (parse-date fmt)))

(defn cached-img-url [opts]
  (str "https://images.weserv.nl/?" (map->query opts)))

(defn join [sep xs]
  (rest (mapcat vector (repeat sep) xs)))

(def interpunct " · ")

(defn unsafe-html
  ([html]
   {:dangerouslySetInnerHTML {:__html html}})
  ([opts html]
   (merge opts (unsafe-html html))))

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
        welcome (->> (concat pages)
                     (filter #((:tags %) "welcome"))
                     first)
        pages (remove #((:tags %) "welcome") pages)]
    (assoc opts
           :site (without-ns site)
           :post (-> item
                     without-ns
                     (update :tags set))
           :posts posts
           :pages pages
           :list (without-ns (first lists))
           :welcome welcome)))

(defn parse-nav-links [{:keys [site]}]
  (->> (:nav-links site)
       str/split-lines
       (map str/trim)
       (remove empty?)
       (map #(str/split % #"\s+" 2))))

;;;; SITE ======================================================================

(defn base-html [{:base/keys [head path] :keys [site] :as opts} & body]
  (let [[title
         description
         image
         canonical] (for [k ["title" "description" "image" "url"]]
                      (or (not-empty (get opts (keyword "base" k)))
                          (not-empty (get-in opts [:post (keyword k)]))
                          (not-empty (get-in opts [:page (keyword k)]))
                          (not-empty (get-in opts [:site (keyword k)]))))]
    [:html
     {:lang "en-US"}
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
      [:meta {:content (str (:url site) path) :property "og:url"}]
      [:link {:ref "canonical" :href (or canonical (str (:url site) path))}]
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
      head]
     [:body
      [:div {:class '[absolute
                      w-full
                      flex
                      flex-col
                      min-h-screen]
             :style {:background-color (:bg-color site)}}
       body
       (when-some [html (:embed-html site)]
         [:div (unsafe-html html)])]]]))

(def errors
  {"invalid-email" "It looks like that email is invalid. Try a different one."
   "recaptcha-failed" "reCAPTCHA check failed. Try again."
   "unknown" "There was an unexpected error. Try again."})

(defn render-page [{:keys [site page account] :as opts}]
  (base-html
   opts
   [:.h-6]
   [:div {:class '[flex
                   flex-col
                   mx-4
                   sm:items-center]}
    [:span.text-xl.font-bold (:title site)]
    [:.h-4]
    [:nav.leading-8
     (join
      [:span.mx-1 interpunct]
      (for [[href label] (parse-nav-links opts)]
        [:a.underline.opacity-95.inline-block {:href href} label]))]]
   [:.h-4]
   [:article {:class '[mx-auto
                       bg-neutral-100
                       prose
                       prose-quoteless
                       lg:prose-lg
                       prose-h1:text-3xl
                       lg:prose-h1:text-4xl
                       w-full
                       px-4]}
    [:div {:style {:height "1px"}}]

    (when (not-empty (:title page))
      [:<>
       [:.h-3]
       [:h1 (:title page)]])
    [:div (or (:rum page)
              (unsafe-html (:html page)))]
    (when-some [date (:published-at page)]
      [:p.text-sm.italic "Published " (format-date date "d MMM yyyy")])
    [:div {:class "h-[1px]"}]]
   [:.h-8]
   [:p.text-center.text-lg.px-4
    [:span.inline-block "Sign up for my newsletter:"]]
   [:form#recaptcha-form.w-full.max-w-xs.sm:max-w-md.mx-auto.px-4
    {:action "/.netlify/functions/subscribe"
     :method "POST"}
    [:script (unsafe-html "function onSubscribe(token) { document.getElementById('recaptcha-form').submit(); }")]
    [:.h-4]
    [:input {:type "hidden"
             :name "href"
             :_ "on load set my value to window.location.href"}]
    [:input {:type "hidden"
             :name "referrer"
             :_ "on load set my value to document.referrer"}]
    [:div.flex.flex-col.sm:flex-row.gap-2
     [:input {:class '[flex-grow]
              :type "email"
              :name "email"
              :placeholder "Enter your email"
              :_ (str "on load "
                      "make a URLSearchParams from window.location.search called p "
                      "then set my value to p.get('email')")}]
     [:button {:class '[hover:opacity-75
                        text-white
                        py-2
                        px-4
                        g-recaptcha]
               :style {:background-color (:accent-color site)}
               :data-sitekey (:recaptcha/site-key account)
               :data-callback "onSubscribe"
               :data-action "subscribe"
               :type "submit"}
      "Subscribe"]]
    (for [[code explanation] errors]
      [:p {:class '[hidden
                    text-center
                    mt-2]
           :_ (str "on load if window.location.search.includes('error="
                   code
                   "') remove .hidden from me")}
       [:span.font-bold "Error:"]
       " "
       explanation])
    [:.h-4]
    [:p.text-center.px-4
     [:a.underline {:href "/feed.xml"} "RSS feed"]
     [:span.mx-1 interpunct]
     [:a.underline {:href "/archive/"} "Archive"]]]
   [:.h-12.grow]
   [:.sm:text-center.text-sm.leading-snug.w-full.px-4.opacity-75
    "This site is protected by reCAPTCHA and the Google "
    [:a.underline {:href "https://policies.google.com/privacy"
                   :target "_blank"}
     "Privacy Policy"] " and "
    [:a.underline {:href "https://policies.google.com/terms"
                   :target "_blank"}
     "Terms of Service"] " apply."]
   [:.h-4]))

(defn atom-feed* [{:keys [posts path site] :as opts}]
  (let [feed-url (str (:url site) path)
        posts (remove #(some (:tags %) ["unlisted" "nofeed"]) posts)]
    [:feed {:xmlns "http://www.w3.org/2005/Atom"}
     [:title (:title site)]
     [:id feed-url]
     [:updated (format-date (:published-at (first posts)))]
     (when-some [url (not-empty (:icon site))]
       [:<>
        [:icon url]
        [:logo url]])
     [:link {:rel "self" :href feed-url :type "application/atom+xml"}]
     [:link {:href (:url site)}]
     (for [post (take 10 posts)
           :let [url (str (:url site) "/p/" (:slug post) "/")]]
       [:entry
        [:title {:type "html"} (:title post)]
        [:id url]
        [:updated (format-date (:published-at post))]
        [:content {:type "html"}
         (:html post)]
        [:link {:href url}]
        [:author
         [:name (:author-name site)]
         (when-some [url (not-empty (:author-url site))]
           [:uri url])]])]))

(defn archive-page [opts]
  {:path "/archive/"
   :title "Archive"
   :rum (for [group (->> (:posts opts)
                         (sort-by :published-at #(compare %2 %1))
                         (map #(assoc % :month (crop-date (:published-at %) "yyyy-MM")))
                         (partition-by :month))]
          [:<>
           [:p (format-date (:month (first group)) "MMMM yyyy")]
           [:ul
            (for [post group]
             [:li [:a {:href (str "/p/" (:slug post) "/")}
                  (:title post)]])]])})

(defn render! [{:keys [dir path doctype contents]
                :or {doctype "<!DOCTYPE html>"}}]
  (let [file (io/file dir (str "public" (str/replace path #"/$" "/index.html")))]
    (io/make-parents file)
    (spit file (str doctype "\n" (rum/render-static-markup contents)))))

(defn pages! [opts]
  (doseq [page (:pages opts)
          :let [path (str/replace (str "/" (:path page) "/") #"/+" "/")
                opts (assoc opts :base/path path :path path :page page)]]
    (render! (assoc opts :contents (render-page opts)))))

(defn posts! [opts]
  (doseq [post (:posts opts)
          :let [path (str/replace (str "/p/" (:slug post) "/") #"/+" "/")
                opts (assoc opts :base/path path :path path :page post)]]
    (render! (assoc opts :contents (render-page opts)))))

(defn atom-feed! [opts]
  (render! (assoc opts
                  :path "/feed.xml"
                  :contents (atom-feed* (assoc opts :path "/feed.xml"))
                  :doctype "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")))

(defn redirects! [{:keys [site dir]}]
  (spit (doto (io/file dir "public/_redirects") io/make-parents)
        (:redirects site)))

(defn netlify-subscribe-fn! [{:keys [db site-id site welcome account dir] lst :list :as opts}]
  (spit (doto (io/file dir "netlify/functions/config.json") io/make-parents)
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
          :recaptchaSecret (:recaptcha/secret-key account)
          :siteUrl (:url site)}))
  (io/copy (io/file (io/resource "com/platypub/themes/default/subscribe.js"))
           (io/file dir "netlify/functions/subscribe.js")))

(defn sitemap! [{:keys [dir exclude]}]
  (let [root (io/file dir "public")]
    (->> (file-seq root)
         (filter #(.isFile %))
         (map #(.getPath %))
         (filter #(str/ends-with? % "index.html"))
         (map (fn [path]
                (-> path
                    (subs (count (.getPath root)))
                    (str/replace #"index.html$" ""))))
         (remove (fn [path]
                   (some #(re-matches % path) exclude)))
         (str/join "\n")
         (spit (doto (io/file dir "public/sitemap.txt") io/make-parents)))))

(defn files! [{:keys [dir] :as opts}]
  (fs/copy-tree (io/file (io/resource "com/platypub/themes/default/public"))
                (io/file dir "public")
                {:replace-existing true})
  (io/copy (io/file (io/resource "com/platypub/themes/default/package.json")) (io/file dir "package.json"))
  (io/copy (io/file (io/resource "com/platypub/themes/default/package-lock.json")) (io/file dir "package-lock.json")))

(defn render-site [opts]
  (let [opts (derive-opts opts)
        opts (update opts :pages into [(archive-page opts)])
        sitemap-exclude (->> (:posts opts)
                             (filter #((:tags %) "unlisted"))
                             (map (fn [post]
                                    (re-pattern (str "/p/" (:slug post) "/")))))]
    (pages! opts)
    (posts! opts)
    (atom-feed! opts)
    (netlify-subscribe-fn! opts)
    (redirects! opts)
    (sitemap! {:exclude (concat [#"/subscribed/" #".*/card/"]
                                sitemap-exclude)
               :dir (:dir opts)})
    (files! opts)))

;;;; EMAIL =====================================================================

(defn centered [& body]
  [:table
   {:border "0",
    :cellpadding "0",
    :align "center",
    :cellspacing "0"
    :style {:width "100%"
            :max-width "550px"}}
   [:tr
    [:td
     [:table
      {:width "100%", :cellspacing "", :cellpadding "0", :border "0"}
      [:tr
       [:td body]]]]]])

(defn space [px]
  [:div {:style {:height (str px "px")}}])

(defn render-email* [{:keys [post site] lst :list :as opts}]
  [:html
   [:head
    [:title (:title post)]
    [:style (-> (io/resource "com/platypub/themes/default/email.css")
                slurp
                (str/replace "$ACCENT_COLOR" (or (:accent-color site) "#06c"))
                unsafe-html)]
    [:meta {:http-equiv "Content-Type" :content "text/html; charset=utf-8"}]
    [:meta {:name "viewport" :content "width=device-width,initial-scale=1"}]]
   [:body {:style {:background-color (:bg-color site)}}
    (centered
     [:div {:style {:font-size "1.5rem"
                    :font-weight "bold"
                    :color "black"
                    :text-align "center"}}
      (:title site)]
     (space 24)
     [:div {:style {:background-color "white"
                    :padding "1rem"}}
      [:.post-content
       [:a {:href (str (:url site) "/p/" (:slug post) "/")
            :style {:text-decoration "none"}}
        [:h1 (:title post)]]
       [:div (unsafe-html (:html post))]]
      (space 40)
      [:hr]
      (space 8)
      [:div {:style {:font-size "85%"}}
       (:mailing-address lst) ". "
       [:a {:href "%mailing_list_unsubscribe_url%"
            :style {:text-decoration "underline"}} "Unsubscribe"] "."]])]])

(defn render-email [opts]
  (let [opts (derive-opts opts)]
    {:subject (get-in opts [:post :title])
     :html (rum/render-static-markup (render-email* opts))}))

;;;; CONFIG ====================================================================

(def all-fields
  {:com.platypub.site/description    {:label "Description"}
   :com.platypub.site/image          {:label "Image URL"
                                      :type :image}
   :com.platypub.site/redirects      {:label "Redirects"
                                      :type :textarea}
   :com.platypub.site/accent-color   {:label "Accent color"
                                      :default "#0369a1"}
   :com.platypub.site/bg-color       {:label "Background color"
                                      :default "#a3a3a3"}
   :com.platypub.site/author-name    {:label "Author name"}
   :com.platypub.site/embed-html     {:label "Embed HTML"
                                      :description "This snippet will be injected into the head of every page. Useful for analytics."
                                      :type :textarea}
   :com.platypub.site/nav-links      {:label "Navigation links"
                                      :default "/ Home\n/archive/ Archive\n/about/ About"
                                      :type :textarea}
   :com.platypub.post/title          {:label "Title"}
   :com.platypub.post/slug           {:label "Slug"
                                      :default [:slugify :com.platypub.post/title]}
   :com.platypub.post/draft          {:label "Draft"
                                      :type :boolean
                                      :default true}
   :com.platypub.post/published-at   {:label "Published"
                                      :type :instant}
   :com.platypub.post/tags           {:label "Tags"
                                      :type :tags}
   :com.platypub.post/description    {:label "Description"
                                      :type :textarea}
   :com.platypub.post/image          {:label "Image"
                                      :type :image}
   :com.platypub.post/canonical      {:label "Canonical URL"}
   :com.platypub.post/html           {:type :html}

   :com.platypub.page/path           {:label "Path"
                                      :default [:slugify :com.platypub.post/title]}})

(def site-fields
  [:com.platypub.site/description
   :com.platypub.site/image
   :com.platypub.site/accent-color
   :com.platypub.site/bg-color
   :com.platypub.site/author-name
   :com.platypub.site/nav-links
   :com.platypub.site/embed-html
   :com.platypub.site/redirects])

(def items
  [{:key :posts
    :label "Post"
    :slug "posts"
    :query [:com.platypub.post/slug]
    :fields [:com.platypub.post/title
             :com.platypub.post/slug
             :com.platypub.post/draft
             :com.platypub.post/published-at
             :com.platypub.post/tags
             :com.platypub.post/description
             :com.platypub.post/image
             :com.platypub.post/canonical
             :com.platypub.post/html]
    :sendable true
    :render/label :com.platypub.post/title
    :render/sections [{:label "Drafts"
                       :match [[:com.platypub.post/draft true]]
                       :order-by [[:com.platypub.post/title :desc]]
                       :show [:com.platypub.post/tags]}
                      {:label "Published"
                       :match [:not [[:com.platypub.post/draft true]]]
                       :order-by [[:com.platypub.post/published-at :desc]]
                       :show [:com.platypub.post/published-at
                              :com.platypub.post/tags]}]}
   {:key :pages
    :label "Page"
    :slug "pages"
    :query [:com.platypub.page/path]
    :fields [:com.platypub.post/title
             :com.platypub.page/path
             :com.platypub.post/draft
             :com.platypub.post/tags
             :com.platypub.post/description
             :com.platypub.post/image
             :com.platypub.post/html]
    :render/label :com.platypub.post/title
    :render/sections [{:order-by [[:com.platypub.page/path :asc]]
                       :show [:com.platypub.page/path]}]}])

(def plugin
  {:label "default"
   :fields all-fields
   :site-fields site-fields
   :items items
   :render-site render-site
   :render-email render-email})
