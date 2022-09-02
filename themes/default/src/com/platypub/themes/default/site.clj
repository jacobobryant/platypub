(ns com.platypub.themes.default.site
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [com.platypub.themes.common :as common]
            [hiccup.util :refer [raw-string]]))

(def footer-text
  [:div.sm:text-center.text-sm.leading-snug.w-full.px-3.pb-3.opacity-75
   "Made with "
   [:a.underline {:href "https://biffweb.com/p/announcing-platypub/"
                  :target "_blank"} "Platypub"]
   ". "
   (common/recaptcha-disclosure {:link-class "underline"})])

(defn logo [{:keys [site] :as opts}]
  [:div (if-some [image (:logo-image site)]
          [:a {:href (:logo-url site)}
           [:img {:src image
                  :alt "Logo"
                  :style {:max-height "30px"}}]]
          (:title site))])

(def hamburger-icon
  [:div.sm:hidden.cursor-pointer
   {:_ "on click toggle .hidden on #nav-menu"}
   (for [_ (range 3)]
     [:div.bg-white
      {:class "h-[4px] w-[30px] my-[6px]"}])])

(defn parse-nav-links [{:keys [site]}]
  (->> (:nav-links site)
       str/split-lines
       (map str/trim)
       (remove empty?)
       (map #(str/split % #"\s+" 2))))

(defn navbar [{:keys [site navbar/max-w navbar/show-logo]
               :or {show-logo true} :as opts}]
  (let [nav-links (parse-nav-links opts)]
    (when (or show-logo (not-empty nav-links))
      (list
       [:div.py-2 {:style {:background-color (:primary-color site)}}
        [:div.flex.mx-auto.items-center.text-white.gap-4.text-lg.flex-wrap.px-3
         {:class (or max-w "max-w-screen-md")}
         (when show-logo (logo opts))
         [:div.flex-grow]
         (for [[href label] nav-links]
           [:a.hover:underline.hidden.sm:block
            {:href href}
            label])
         (when (not-empty nav-links)
           hamburger-icon)]]
       (when (not-empty nav-links)
         [:div#nav-menu.px-5.py-2.text-white.text-lg.hidden.transition-all.ease-in-out.sm:hidden
          {:style {:background-color (:primary-color site)}}
          (for [[href label] (parse-nav-links opts)]
            [:div.my-2 [:a.hover:underline.text-lg {:href href} label]])])))))

(defn byline [{:keys [post site byline/card] :as opts}]
  [:div
   {:style {:display "flex"
            :align-items "center"}}
   [:img (if card
           {:src (:author-image site)
            :width "115px"
            :height "115px"
            :style {:border-radius "50%"}}
           {:src (common/cached-img-url {:url (:author-image site)
                                         :w 200 :h 200})
            :width "50px"
            :height "50px"
            :style {:border-radius "50%"}})]
   [:div {:style {:width "0.75rem"}}]
   [:div
    [:div {:style {:line-height "1.25"}}
     [:a.hover:underline
      {:class (if card
                "text-[2.5rem]"
                "text-blue-600")
       :href (:author-url site)
       :target "_blank"}
      (:author-name site)]]
    [:div {:class (if card "text-[2.2rem]" "text-[90%]")
           :style {:line-height "1"
                   :color "#4b5563"}}
     (common/format-date "d MMM yyyy" (:published-at post))]]])

(def errors
  {"invalid-email" "It looks like that email is invalid. Try a different one."
   "recaptcha-failed" "reCAPTCHA check failed. Try again."
   "unknown" "There was an unexpected error. Try again."})

(defn subscribe-form [{:keys [site account show-read-more] lst :list}]
  [:div.flex.flex-col.items-center.text-center.px-3.text-white
   {:style {:background-color (:primary-color site)}}
   (if (not-empty (:home-logo site))
     (list
      [:div.h-16]
      [:img.w-full {:src (:home-logo site)
                    :style {:max-width "500px"}}])
     (list
      [:div.h-20]
      [:div.font-bold.text-3xl.leading-none
       (:title lst)]
      [:div.h-4]))
   [:div.text-lg.md:text-xl (:description site)]
   [:div.h-6]
   [:script (raw-string "function onSubscribe(token) { document.getElementById('recaptcha-form').submit(); }")]
   [:form#recaptcha-form.w-full.max-w-md
    {:action "/.netlify/functions/subscribe"
     :method "POST"}
    [:input {:type "hidden"
             :name "href"
             :_ "on load set my value to window.location.href"}]
    [:input {:type "hidden"
             :name "referrer"
             :_ "on load set my value to document.referrer"}]
    [:div.flex.flex-col.sm:flex-row.gap-1.5
     [:input {:class '[rounded
                       shadow
                       border-gray-300
                       focus:ring-0
                       focus:ring-transparent
                       focus:border-gray-300
                       flex-grow
                       text-black]
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
                        rounded
                        shadow
                        g-recaptcha]
               :style {:background-color (:accent-color site)}
               :data-sitekey (:recaptcha/site account)
               :data-callback "onSubscribe"
               :data-action "subscribe"
               :type "submit"}
      "Subscribe"]]
    (for [[code explanation] errors]
      [:div.hidden.text-left.mt-1
       {:_ (str "on load if window.location.search.includes('error="
                code
                "') remove .hidden from me")}
       explanation])]
   (when show-read-more
     (list
      [:div.h-6]
      [:a.block.text-center.underline
       {:href "/"}
       "Read it first"]))
   [:div.h-20]])

(defn post-page [{:keys [site post account base/path] :as opts}]
  (let [width (if ((:tags post) "video")
                "max-w-screen-lg"
                "max-w-screen-sm")]
    (common/base-html
      opts
      (navbar (assoc opts :navbar/max-w width))
      [:div.mx-auto.p-3.text-lg.flex-grow.w-full
       {:class width}
       [:div.h-2]
       [:h1.font-bold.leading-tight.text-gray-900
        {:style {:font-size "2.75rem"}}
        (:title post)]
       [:div.h-3]
       (byline opts)
       [:div.h-5]
       [:div.post-content (raw-string (:html post))]
       [:div.h-5]
       (when-some [forum-url (not-empty (:discourse-url site))]
         (list
           [:div.text-xl.font-bold.mb-3 "Comments"]
           (common/embed-discourse {:forum-url forum-url
                                    :page-url (str (:url site) path)})
           [:div.h-5]))]
      (subscribe-form opts)
      [:div
       {:style {:background-color (:primary-color site)}}
       [:div.text-white footer-text]])))

(defn render-page [{:keys [site page account] :as opts}]
  (common/base-html
    opts
    (navbar opts)
    [:div.mx-auto.px-3.pb-3.text-lg.flex-grow.w-full.max-w-screen-md
     [:div.post-content (raw-string (:html page))]]))

(defn post-list-item [post]
  [:a.block.mb-5.bg-white.rounded.p-3.cursor-pointer.w-full
   {:href (str "/p/" (:slug post) "/")
    :class "hover:bg-white/50"}
   [:div.text-sm.text-gray-800 (common/format-date "d MMM yyyy" (:published-at post))]
   [:div.text-xl.font-bold (:title post)]
   [:div (:description post)]])

(defn archive-page [{:keys [posts site] lst :list :as opts}]
  (common/base-html
    (assoc opts :base/title "Archive")
    (navbar opts)
    [:div.h-full.flex-grow.flex.flex-col
     {:style {:background-color (:tertiary-color site)}}
     [:div.h-5]
     [:div.max-w-screen-md.mx-auto.px-3.w-full
      (->> posts
           (remove #((:tags %) "unlisted"))
           (map post-list-item))]
     [:div.flex-grow]]
    (subscribe-form opts)
    [:div
     {:style {:background-color (:primary-color site)}}
     [:div.text-white footer-text]]))

(defn landing-page-posts [{:keys [posts site] :as opts}]
  (let [posts (remove #((:tags %) "unlisted") posts)
        featured (filter #((:tags %) "featured") posts)
        recent (->> posts
                    (remove #((:tags %) "featured"))
                    (take 5))]
    [:div.h-full.flex-grow.flex.flex-col.p-6
     {:style {:background-color (:tertiary-color site)}}

     (when (not-empty featured)
       (list
        [:div.text-2xl.text-center "Featured"]
        [:div.h-6]
        [:div.max-w-screen-md.mx-auto.w-full
         (map post-list-item featured)]
        [:div.h-6]))

     (when (not-empty recent)
       (list
        [:div.text-2xl.text-center "Recent"]
        [:div.h-6]
        [:div.max-w-screen-md.mx-auto.w-full
         (map post-list-item recent)]))

     [:div.h-6]
     [:a.text-xl.underline.block.text-center
      {:href "/archive/"}
      "View all posts"]
     [:div.h-12]
     [:div.flex-grow]
     footer-text]))

(defn about-section [{:keys [about site] :as opts}]
  [:div.mx-auto.px-6.lg:px-12.pb-6.text-lg.flex-grow.w-full.max-w-screen-md
   {:style {:margin-top "-63px"}}
   [:div
    [:a.block.bg-black.inline-block
     {:href (:author-url site) :target "_blank"
      :style {:border-radius "50%"
              :border "3px solid white"}}
     [:img.hover:opacity-80
      {:src (common/cached-img-url {:url (:author-image site)
                                    :w 240 :h 240})
       :height "120px"
       :width "120px"
       :style {:border-radius "50%"}}]]]
   [:a.text-2xl.block.font-bold.hover:underline {:href (:author-url site) :target "_blank"} (:author-name site)]
   [:div.h-6]
   [:div.post-content (raw-string (:html about))]])

(defn landing-page [{:keys [posts site about] lst :list :as opts}]
  (common/base-html
    (assoc opts :base/title (:title lst))
    (navbar (assoc opts :navbar/show-logo (empty? (:home-logo site))))
    (subscribe-form opts)
    (when about
      [:div.h-6 {:style {:background-color (:primary-color site)}}])
    (if about
      [:div.lg:grid.grid-cols-2
       (about-section opts)
       (landing-page-posts opts)]
      (landing-page-posts opts))))

(defn subscribe-page [{:keys [posts site about] lst :list :as opts}]
  (common/base-html
    (assoc opts :base/title (str "Subscribe to " (:title lst)))
    [:div.flex-grow {:style {:background-color (:primary-color site)}}]
    (subscribe-form (assoc opts :show-read-more true))
    [:div.flex-grow {:style {:background-color (:primary-color site)}}]
    [:div.flex-grow {:style {:background-color (:primary-color site)}}]
    [:div.text-white {:style {:background-color (:primary-color site)}}
     footer-text]))

(def pages
  {"/" landing-page
   "/archive/" archive-page
   "/subscribe/" subscribe-page})

(defn render-card [{:keys [site post] :as opts}]
  (common/base-html
    opts
    [:div.mx-auto.border.border-black
     {:style "width:1202px;height:620px"}
     [:div.flex.flex-col.justify-center.h-full.p-12
      [:div [:img {:src "/images/card-logo.png"
             :alt "Logo"
             :style {:max-height "60px"}}]]
      [:div {:class "h-[1.5rem]"}]
      [:h1.font-bold.leading-none
       {:class "text-[6rem]"}
       (str/replace (:title post) #"^\[draft\] " "")]
      [:div {:class "h-[2.5rem]"}]
      (byline (assoc opts :byline/card true))]]))

(defn cards! [{:keys [posts] :as opts}]
  ;; In Firefox, you can inspect element -> screenshot node, then use as the
  ;; post image (for social media previews).
  (doseq [post posts
          :let [path (str "/p/" (:slug post) "/card/")]]
    (common/render! path
                    "<!DOCTYPE html>"
                    (render-card (assoc opts :base/path path :post post)))))

(defn assets!
  "Deprecated"
  []
  (->> (file-seq (io/file "assets"))
       (filter #(.isFile %))
       (run! #(io/copy % (doto (io/file "public" (subs (.getPath %) (count "assets/"))) io/make-parents)))))

(defn -main []
  (let [opts (common/derive-opts (edn/read-string (slurp "input.edn")))
        sitemap-exclude (->> (:posts opts)
                             (filter #((:tags %) "unlisted"))
                             (map (fn [post]
                                    (re-pattern (str "/p/" (:slug post) "/")))))]
    (common/redirects! opts)
    (common/netlify-subscribe-fn! opts)
    (common/pages! opts render-page pages)
    (common/posts! opts post-page)
    (common/atom-feed! opts)
    (common/sitemap! {:exclude (concat [#"/subscribed/" #".*/card/"]
                                       sitemap-exclude)})
    (cards! opts)
    (assets!)
    (when (fs/exists? "main.css")
      (io/make-parents "public/css/_")
      (common/safe-copy "main.css" "public/css/main.css")))
  nil)
