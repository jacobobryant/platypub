(ns com.platypub.ui
  (:require [clojure.java.io :as io]
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
        (merge #:base{:title "My Application"
                      :lang "en-US"
                      :icon "/img/glider.png"
                      :description "My Application Description"
                      :image "https://clojure.org/images/clojure-logo-120b.png"})
        (update :base/head (fn [head]
                             (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                      [:script {:src "https://unpkg.com/htmx.org@1.6.1"}]
                                      [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]]
                                     head))))
    body))

(def nav-options
  [{:name :posts
    :label "Posts"
    :href "/"}
   {:name :sites
    :label "Sites"
    :href "/sites"}
   {:name :newsletters
    :label "Newsletters"
    :href "/newsletters"}])

(defn nav-page [{:keys [current email] :as opts} & body]
  (base
    {:base/head [[:script (biff/unsafe (slurp (io/resource "darkmode.js")))]]}
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
    [:.p-3.ml-64.bg-gray-100.dark:bg-stone-800.dark:text-gray-50.min-h-screen body]))

(defn page [opts & body]
  (base
    opts
    [:.p-3.mx-auto.max-w-screen-sm.w-full
     body]))

(defn text-input [{:keys [id label element]
                   :or {element :input}
                   :as opts}]
  (list
    [:label.block.text-sm {:for id} label]
    [:.h-1]
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
                    (dissoc opts :label))]))

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
