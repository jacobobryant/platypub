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
  (or (not-empty (get-in opts [:post :comments-url]))
      (not-empty (get-in opts [:site :comments-url]))
      (when (not-empty (post-url opts))
        (str (post-url opts) "#discourse-comments"))))

(defn comments-enabled? [{:keys [post site]}]
  (or (not-empty (:comments-url post))
      (not-empty (:comments-url site))
      (not-empty (:discourse-url site))))

(defn byline [{:keys [post site] :as opts}]
  (let [image [:img {:src (common/cached-img-url {:url (:author-image site)
                                                  :w 100 :h 100})
                     :width "50px"
                     :height "50px"
                     :style {:border-radius "50%"
                             :width "50px"
                             :height "50px"
                             :margin-right "10px"}}]]
    [:table
     {:border "0"
      :cellpadding "0"
      :cellspacing "0"}
     [:tr
      [:td (if-some [url (not-empty (:author-url site))]
             [:a {:href (:author-url site)
                  :style {:text-decoration "none"}} image]
             image)]
      [:td
       [:div {:style {:line-height "120%"}} (:author-name site)]
       [:div {:style {:line-height "120%"}}
        (common/format-date "d MMM yyyy" (:published-at post))
        (when (comments-enabled? opts)
          (list
           common/interpunct
           [:a {:href (comments-url opts)} "comments"]))]]]]))

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
                  :text-align "center"
                  :text-decoration "none"}}
      label]]]])

(defn render [{:keys [post site] lst :list :as opts}]
  [:html
   [:head
    [:title (:title post)]
    [:style (-> (io/resource "com/platypub/themes/default/email.css")
                slurp
                (str/replace "$ACCENT_COLOR" (or (:accent-color site) "#06c"))
                (str/replace "$LINK_COLOR" (or (:link-color site) "inherit"))
                raw-string)]
    [:meta {:http-equiv "Content-Type" :content "text/html; charset=utf-8"}]
    [:meta {:name "viewport" :content "width=device-width,initial-scale=1"}]]
   [:body
    (centered
     (if-some [img (not-empty (:logo-image site))]
       [:a {:href (:url site)
            :style {:text-decoration "none"}}
        [:div
         {:style {:background-color (:primary-color site)
                  :padding "0.5rem 1rem"
                  :margin-bottom "1.5rem"}}
         [:img {:src img
                :style {:max-height "30px"
                        :display "block"}
                :alt (:title lst)}]]]
       [:div {:style {:margin "10px 0"}}
      [:a {:href (:url site)} (:title lst)]])
     (let [title [:h1.title {:style {:font-size "2.75rem"
                                     :margin "0"
                                     :color "black"
                                     :line-height "1.15"}}
                  (:title post)]]
       (if (-> post :slug not-empty)
         [:a {:href (post-url opts)
              :style {:text-decoration "none"}} title]
         title))
     (space 15)
     (byline opts)
     (space 10)
     [:div.post-content (raw-string (:html post))]
     (space 15)
     (when (comments-enabled? opts)
       (button {:bg-color (:primary-color site)
                :href (comments-url opts)
                :label "Discuss this post"}))
     (space 25)
     [:hr]
     (space 8)
     [:div {:style {:font-size "85%"}}
      (:mailing-address lst) ". "
      [:a {:href "%mailing_list_unsubscribe_url%"} "Unsubscribe"] "."])]])

(defn -main []
  (let [opts (common/derive-opts (edn/read-string (slurp *in*)))]
    (prn {:subject (get-in opts [:post :title])
          :html (str (hiccup/html (render opts)))})))
