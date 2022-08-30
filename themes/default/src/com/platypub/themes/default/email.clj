(ns com.platypub.themes.default.email
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.edn :as edn]
            [com.platypub.themes.common :as common]
            [hiccup2.core :as hiccup]
            [hiccup.util :refer [raw-string]]))

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

(defn post-url [{:keys [post site] :as opts}]
  (when (not-empty (:slug post))
    (str (:url site) "/p/" (:slug post) "/")))

(defn comments-url [opts]
  (when (not-empty (post-url opts))
    (str (post-url opts) "#discourse-comments")))

(defn byline [{:keys [post site] :as opts}]
  [:table
   {:border "0"
    :cellpadding "0"
    :cellspacing "0"}
   [:tr
    [:td [:a {:href (:author-url site)}
          [:img {:src (common/cached-img-url {:url (:author-image site)
                                              :w 100 :h 100})
                 :width "50px"
                 :height "50px"
                 :style {:border-radius "50%"
                         :width "50px"
                         :height "50px"
                         :margin-right "10px"}}]]]
    [:td {:style {:font-size "90%"}}
     [:div (:author-name site)]
     [:div
      (common/format-date "d MMM yyyy" (:published-at post))
      (when (not-empty (:discourse-url site))
        (list
         common/interpunct
         [:a {:href (comments-url opts)} "comments"]))]]]])

(defn space [px]
  [:div {:style (str "height:" px "px")}])

(defn button [{:keys [bg-color href label]}]
  [:table {:width "100%", :cellspacing "", :cellpadding "0", :border "0"}
   [:tr
    [:td
     {:align "center"}
     [:a {:href href
          :style {:background-color bg-color
                  :color "white"
                  :padding "10px 20px"
                  :border-radius "3px"
                  :display "inline-block"
                  :text-align "center"}}
      label]]]])

(defn render [{:keys [post site] lst :list :as opts}]
  [:html
   [:head
    [:title (:title post)]
    [:style (raw-string (slurp (io/resource "com/platypub/themes/default/email.css")))]
    [:meta {:http-equiv "Content-Type" :content "text/html; charset=utf-8"}]
    [:meta {:name "viewport" :content "width=device-width,initial-scale=1"}]]
   [:body
    (centered
     [:div {:style {:margin "10px 0"}}
      [:a {:href (:url site)} (:title lst)]]
     (let [title [:h1.title {:style {:font-size "2.25rem"
                                     :margin "0"
                                     :color "black"
                                     :line-height "1.15"}}
                  (:title post)]]
       (if (-> post :slug not-empty)
         [:a {:href (post-url opts)} title]
         title))
     (space 5)
     (byline opts)
     (space 10)
     [:div.post-content (raw-string (:html post))]
     (space 15)
     (when (not-empty (:discourse-url site))
       (list
        (button {:bg-color (:primary-color site)
                 :href (comments-url opts)
                 :label "View comments"})
        (space 25)))
     [:hr]
     (space 8)
     [:div {:style {:font-size "85%"}}
      (:mailing-address lst) ". "
      [:a {:href "%mailing_list_unsubscribe_url%"} "Unsubscribe."]])]])

(defn -main []
  (let [opts (common/derive-opts (edn/read-string (slurp *in*)))]
    (prn {:subject (get-in opts [:post :title])
          :html (str (hiccup/html (render opts)))})))
