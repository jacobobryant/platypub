(ns com.platypub.ui
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.set :as set]
            [com.biffweb :as biff]
            [com.platypub.util :as util]
            [com.platypub.settings :as settings]
            [ring.middleware.anti-forgery :as csrf]
            [rum.core :as rum]))

(def interpunct " · ")

;; useful if you want to prevent gmail from formatting something as a link
(def fake-period "․")

;; https://orioniconlibrary.com/icon/sun-and-moon-4452

(def sun-moon-svg
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 64 64"
         :height 32
         :width 32
         :aria-labelledby "title"
         :aria-describedby "desc"
         :role "img"
         :xmlns:xlink= "http://www.w3.org/1999/xlink"}
   [:title "Sun And Moon"]
   [:desc "A solid styled icon from Orion Icon Library."]
   [:path {:data-name "layer2"
           :d "M36.4 20.4a16 16 0 1 0 16 16 16 16 0 0 0-16-16zm0 28a12 12 0 0 1-10.3-5.8l2.5.3A13.7 13.7 0 0 0 42 25.8a12 12 0 0 1-5.6 22.6z"
           :fill "#d97706"}]
   [:path {:data-name "layer1"
           :d "M36.4 16.4a2 2 0 0 0 2-2v-8a2 2 0 1 0-4 0v8a2 2 0 0 0 2 2zm-20 20a2 2 0 0 0-2-2h-8a2 2 0 0 0 0 4h8a2 2 0 0 0 2-2zm3-14.1a2 2 0 0 0 2.8-2.8l-5.7-5.7a2 2 0 0 0-2.8 2.8zM59 13.8a2 2 0 0 0-2.8 0l-5.7 5.7a2 2 0 1 0 2.8 2.8l5.7-5.7a2 2 0 0 0 0-2.8zM19.4 50.5l-5.7 5.7a2 2 0 1 0 2.9 2.8l5.7-5.7a2 2 0 1 0-2.8-2.8z"
           :fill "#d97706"}]])

(defn css-path []
  (if-some [f (io/file (io/resource "public/css/main.css"))]
    (str "/css/main.css?t=" (.lastModified f))
    "/css/main.css"))

(defn base [{:keys [::recaptcha] :as opts} & body]
  (apply
   biff/base-html
   (-> opts
       (merge #:base{:title settings/app-name
                     :lang "en-US"
                     :description (str settings/app-name " Description")})
       (update :base/head (fn [head]
                            (concat [[:script {:src "https://unpkg.com/htmx.org@1.7.0"}]
                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.5"}]
                                     (when recaptcha
                                       [:script {:src "https://www.google.com/recaptcha/api.js"
                                                 :async "async" :defer "defer"}])
                                     [:link {:rel "apple-touch-icon", :sizes "180x180", :href "/apple-touch-icon.png"}]
                                     [:link {:rel "icon", :type "image/png", :sizes "32x32", :href "/favicon-32x32.png"}]
                                     [:link {:rel "icon", :type "image/png", :sizes "16x16", :href "/favicon-16x16.png"}]
                                     [:link {:rel "manifest", :href "/site.webmanifest"}]
                                     [:link {:rel "mask-icon", :href "/safari-pinned-tab.svg", :color "#5bbad5"}]
                                     [:meta {:name "msapplication-TileColor", :content "#da532c"}]
                                     [:meta {:name "theme-color", :content "#ffffff"}]]
                                    head
                                    [[:link {:rel "stylesheet" :href (css-path)}]]))))
   body))

(def nav-options
  [{:name :sites
    :label "Sites"
    :href "/sites"}
   {:name :newsletters
    :label "Newsletters"
    :href "/newsletters"}])

(defn pills [{:keys [options active]}]
  (for [{:keys [name label href]} options]
    [:a.mr-1.p-2
     {:class (if (= active name)
               "btn-secondary rounded text-white"
               "link")
      :href href}
     label]))

(defn sidebar-link [{:keys [active label href]}]
  [:a.block.p-2.mx-3.rounded.mb-1
   {:class (if active
             '[text-white
               bg-stone-800]
             '[text-white
               hover:bg-stone-800])
    :href href}
   label])

(defn nav-page [{:keys [current sites user] :as sys} & body]
  (base
   {:base/head [[:script (biff/unsafe (slurp (io/resource "darkmode.js")))]]}
   (list

    ;;; Sidebar

    [:div.hidden.md:block
     [:div {:class '[w-64
                     bg-stone-900
                     text-white
                     fixed
                     h-screen
                     flex
                     flex-col]}
      [:.h-3]
      [:.text-xl.mx-3 "Platypub"]
      [:.h-6]
      (for [{:keys [site/title site.config/items xt/id] :as site} (sort-by :site/title sites)
            :when (not-empty items)]
        (list
         [:.mx-3.text-gray-400.uppercase.text-sm title]
         [:.h-1]
         [:hr.border-stone-600]
         [:.h-2]
         (for [item items]
           (sidebar-link {:href (util/make-url "/site" id (:slug item))
                          :label (str (:label item) "s")
                          :active (= [site item]
                                     [(:site sys) (:item-spec sys)])}))
         [:.h-3]))
      [:hr.border-stone-600]
      [:.h-3]
      (for [{:keys [name label href]} nav-options]
        (sidebar-link {:href href
                       :label label
                       :active (= name current)}))
      [:.flex-grow]
      [:button.btn.mx-6.my-3 {:onclick "toggleDarkMode()"} "Toggle dark mode"]
      [:.px-6.text-sm (:user/email user)]
      [:.px-6.text-sm
       (biff/form
        {:action "/auth/signout"
         :class "inline"}
        [:button.text-amber-600.hover:underline {:type "submit"}
         "sign out"])]
      [:.h-3]]
     [:div {:class '[ml-64
                     p-3
                     bg-gray-100
                     dark:bg-stone-800
                     dark:text-gray-50
                     min-h-screen]}
      body]]

    ;;; Pills

    [:div.md:hidden
     {:class '[flex
               flex-col
               h-screen
               bg-stone-900
               text-gray-100]}
     [:div {:class '[flex
                     justify-between]}
      [:div {:class '[block
                      p-3
                      text-xl
                      text-white]} "Platypub"]
      [:.p-3 (:user/email user) " | "
       (biff/form
        {:action "/auth/signout"
         :class "inline"}
        [:button.hover:underline {:type "submit"}
         "sign out"])]]
     [:div#body {:class '[p-3
                          text-black
                          bg-gray-100
                          dark:bg-stone-800
                          dark:text-gray-50
                          min-h-screen]}
      [:.flex.flex-wrap
       (pills {:options nav-options
               :active current})
       [:.flex-grow]
       [:a {:href "#"
                 :onclick "toggleDarkMode()"} sun-moon-svg]]
      [:.h-5]
      body]])))

(defn page [opts & body]
  (base
   opts
   [:.flex-grow]
   [:.p-3.mx-auto.max-w-screen-sm.w-full
    (when (bound? #'csrf/*anti-forgery-token*)
      {:hx-headers (cheshire/generate-string
                    {:x-csrf-token csrf/*anti-forgery-token*})})
    body]
   [:.flex-grow]
   [:.flex-grow]))

(defn input [{:keys [id label description element type]
                   :or {element :input type "text"}
                   :as opts}]
  (list
   (when label
     [:label.block.text-sm.mb-1 {:for id} label])
   [element (merge {:type type
                    :class '[w-full
                             border-gray-300
                             rounded
                             disabled:bg-slate-50
                             disabled:text-slate-500
                             disabled:border-slate-200
                             dark:bg-stone-600
                             dark:border-stone-600
                             dark:disabled:text-stone-400]
                    :name id}
                   (dissoc opts :label :description))]
   (when description
     [:.text-sm.text-gray-600.dark:text-gray-400 description])))

(defn text-input [opts]
  (input (merge opts {:type "text"})))

(defn textarea [opts]
  (text-input (assoc opts :element :textarea)))

(defn checkbox [{:keys [id name value label] :as opts}]
  (list
   [:label.block.text-sm {:for id} label]
   [:.h-1]
   [:input.cursor-pointer.border.form-checkbox.border-gray-400.block
    (merge {:type "checkbox"
            :class '[curson-pointer
                     border
                     form-checkbox
                     border-gray-400
                     block]
            :name id}
           (dissoc opts :label))]))

(defn image [{:keys [value] :as opts}]
  (list
   (text-input opts)
   (when (not-empty value)
     [:.mt-3.flex.justify-center
      [:img {:src value
             :style {:max-height "10rem"}}]])))

(defn select
  [{:keys [id options default label] :as opts}]
  [:div
   [:label.block.text-sm {:for id} label]
   [:.h-1]
   [:select.cursor-pointer
    (-> opts
        (dissoc :options :default :outer-class)
        (assoc :class '[appearance-none
                        border
                        border-gray-300
                        py-1
                        px-2
                        pr-10
                        rounded
                        leading-tight
                        focus:outline-none
                        focus:shadow-outline
                        bg-white
                        text-black
                        dark:bg-stone-600
                        dark:border-stone-600
                        dark:text-gray-300]))
    (for [{:keys [label value]} options]
      [:option.cursor-pointer
       {:value value
        :selected (when (= value default) "selected")}
       label])]])

(defn radio [{:keys [options id name default label] :as opts}]
  (list
   [:label.block.text-sm {:for id} label]
   [:.h-1]
   (for [{:keys [label value]} options]
     [:label.flex.items-center.cursor-pointer.py-1.text-sm
      [:input.cursor-pointer.form-radio
       {:type "radio" :name name :value value
        :checked (when (= default value)
                   "checked")}]
      [:span.ml-2 label]])))

(defn color [{:keys [id label value description] :as opts}]
  (let [text-input-opts (assoc (dissoc opts :label :description) :_ "on blur tell the previous <input[type=color] /> set your value to my value")]
    (list
     [:label.block.text-sm.mb-1 {:for id} label]
     [:.flex {:id id}
      [:input {:_ "on change tell the next <input[type=text] /> set your value to my value" :type "color" :value value :class "mr-2" :style {:height "42px" :width "42px"}}]
      (text-input text-input-opts)]
     (when description
       [:.text-sm.text-gray-600.dark:text-gray-400 description]))))

(def not-found-response
  {:status 404
   :headers {"content-type" "text/html"}
   :body (rum/render-static-markup [:h1 "Not found"])})

(defn custom-field [{:keys [site item]} k]
  (let [{:keys [label type default description]} (get-in site [:site.config/fields k])
        value (if item
                (get item (util/add-prefix "item.custom." k))
                (get site (util/add-prefix "site.custom." k)))
        opts {:id (name k)
              :label label
              :value value
              :description (->> [(when default
                                   (str "Default: "
                                        (pr-str default)))
                                 description]
                                (filter some?)
                                (str/join ". ")
                                not-empty)}]
    (case type
      :textarea (textarea opts)
      :instant (input {:id (name k)
                       :type "datetime-local"
                       :label (str label " (UTC)")
                       :step 1
                       :value (some-> value
                                      (.toInstant)
                                      (.toString)
                                      (str/split #"(\.|Z)")
                                      first)})
      :boolean (checkbox (set/rename-keys opts {:value :checked}))
      :tags (text-input (assoc opts :value (str/join " " value)))
      :image (list
              (text-input opts)
              (when (not-empty value)
                [:.mt-3.flex.justify-center
                 [:img {:src value
                        :style {:max-height "10rem"}}]]))
      :color (color opts)
      (text-input opts))))
