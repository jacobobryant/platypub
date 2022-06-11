(ns com.platypub.ui
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [com.biffweb :as biff]))

(def interpunct " · ")

(defn css-path []
  (if-some [f (io/file (io/resource "public/css/main.css"))]
    (str "/css/main.css?t=" (.lastModified f))
    "/css/main.css"))

(defn base [opts & body]
  (apply
    biff/base-html
    (-> opts
        (merge #:base{:title "Platypub"
                      :lang "en-US"
                      :icon "/img/glider.png"
                      :description ""
                      :image "https://clojure.org/images/clojure-logo-120b.png"})
        (update :base/head (fn [head]
                             (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                      [:script {:src "https://unpkg.com/htmx.org@1.7.0"}]
                                      [:script {:src "https://unpkg.com/hyperscript.org@0.9.5"}]]
                                     head))))
    body))

(def hamburger-icon
  [:div.md:hidden.cursor-pointer.p-4
   {:_ "on click toggle .hidden on #body
        on click toggle .hidden on #menu-items
        on click toggle .hidden on #prefs"}
   (for [_ (range 3)]
     [:div.bg-white
      {:class "h-[4px] w-[30px] my-[6px]"}])])

(def nav-options
  [{:name :posts
    :label "Posts"
    :href "/app"}
   {:name :sites
    :label "Sites"
    :href "/sites"}
   {:name :newsletters
    :label "Newsletters"
    :href "/newsletters"}])

(defn nav-page [{:keys [current email]} & body]
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
      [:.text-xl.mx-6 "Platypub"]
      [:.h-6]
      (for [{:keys [name label href]} nav-options]
        [:a.block.p-3.mx-3.rounded.mb-1 {:class (if (= name current)
                                                  '[text-white
                                                    bg-stone-800]
                                                  '[text-gray-400
                                                    hover:bg-stone-800])
                                         :href href}
         label])
      [:.flex-grow]
      [:button.btn.mx-6.my-3 {:onclick "toggleDarkMode()"} "Toggle dark mode"]
      [:.px-6.text-sm email]
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
    
    ;;; Dropdown

    [:div.md:hidden
     {:class '[flex
               flex-col
               h-screen
               bg-stone-900
               text-gray-100]}
     [:div {:class '[flex
                     justify-between]}
      [:a {:href "#"
           :class '[block
                    text-xl
                    mx-3
                    p-3
                    text-white]} "Platypub"]
      hamburger-icon]
     [:div#menu-items.hidden.flex.flex-col
      (for [{:keys [name label href]} nav-options]
        [:a.block.p-3.mx-3.rounded.mb-1
         {:class (if (= name current)
                   '[text-white
                     bg-stone-800]
                   '[text-gray-400
                     hover:bg-stone-800])
          :href href}
         label])]
     [:.flex-grow]
     [:div#prefs.hidden
      [:button.btn.mx-6.my-3 {:onclick "toggleDarkMode()"} "Toggle dark mode"]
      [:.px-6.text-sm email]
      [:.px-6.text-sm
       (biff/form
        {:action "/auth/signout"
         :class "inline"}
        [:button.text-amber-600.hover:underline {:type "submit"}
         "sign out"])]
      [:.h-3]]
      [:div#body {:class '[p-3
                           text-black
                           bg-gray-100
                           dark:bg-stone-800
                           dark:text-gray-50
                           min-h-screen]}
       body]])))

(defn page [opts & body]
  (base
   opts
   [:.p-3.mx-auto.max-w-screen-sm.w-full
    body]))

(defn text-input [{:keys [id label description element]
                   :or {element :input}
                   :as opts}]
  (list
    (when label
      [:label.block.text-sm.mb-1 {:for id} label])
    [element (merge {:type "text"
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
                        pr-6
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
