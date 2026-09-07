(ns com.platypub.app.demo
  (:require [com.biffweb.datastar :as biff.datastar :refer [signal-name]]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.middleware :as mid]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]))

(defpath set-display-name-path "/app/display-name")
(defpath set-background-color-path "/app/background-color")

;;;; Page ======================================================================

(def background-colors [:white :red :blue :green])

(defn- background-class [background-color]
  (case background-color
    :red   "bg-red-100"
    :blue  "bg-blue-100"
    :green "bg-green-100"
    "bg-white"))

(defpipeline demo-page
  [:biff.graph.fx/query
   [{:session/user [[:? :user/display-name]]}
    {:request/tab [:tab/background-color]}]]

  (fn [request {:keys [session/user request/tab]}]
    (let [{:user/keys [display-name]}    user
          {:tab/keys [background-color]} tab]
      (ui/app-page
       request
       [:main {:id    "demo-page"
               :class ["grid flex-1 grid-rows-[1fr_auto_2fr]"
                       "justify-items-center p-8"
                       (background-class background-color)]

               :data-signals__ifmissing
               (biff.datastar/signals-json
                {:user/display-name    (or display-name "")
                 :tab/background-color background-color})}
        [:div {:class ["row-start-2 flex flex-col items-center gap-4"]}
         [:h1 {:class ["text-2xl font-semibold"]}
          (if (seq display-name)
            (str "Hello, " display-name ".")
            "Enter your name.")]
         [:form {:class          ["flex flex-col gap-4"]
                 :data-action    (set-display-name-path)
                 :data-on:submit "@post(el.dataset.action)"}
          [:label {:class ["flex flex-col gap-2"]}
           "Display name:"
           [:input {:type         "text"
                    :class        ["rounded border p-2"]
                    :autocomplete "nickname"
                    :data-bind    (signal-name :user/display-name)}]]
          [:button {:type  "submit"
                    :class ["rounded bg-blue-600 px-4 py-2 text-white"]}
           "Save"]]
         [:label {:class ["flex items-center gap-2"]}
          "Background color"
          [:select {:class          ["rounded border p-2"]
                    :data-bind      (signal-name :tab/background-color)
                    :data-action    (set-background-color-path)
                    :data-on:change "@post(el.dataset.action)"}
           (for [color background-colors]
             [:option {:value (name color)} (name color)])]]
         [:a {:class ["text-blue-600 hover:underline"] :href (routes/admin)}
          "Admin dashboard"]
         [:form {:method "post" :action (routes/signout)}
          [:button {:class ["text-blue-600 hover:underline"] :type "submit"}
           "Sign out"]]]]))))

;;;; Actions ===================================================================

(defpipeline set-display-name
  (fn [{:keys [session] :biff.datastar/keys [signals]}]
    {:_      [:biff.sqlite.fx/authorized-write
              {:update :user
               :set    {:user/display-name (:user/display-name signals)}
               :where  [:= :user/id (:uid session)]}]
     :status 204}))

(defpipeline set-background-color
  (fn [{:biff.datastar/keys [signals tab-id]}]
    (let [background-color (some-> (:tab/background-color signals) keyword)
          new-tab-state    {:tab/background-color background-color}]
      {:_      [:biff.sqlite.fx/authorized-write
                {:insert-into   :tab-state
                 :values        [{:tab-state/id   tab-id
                                  :tab-state/data [:lift new-tab-state]}]
                 :on-conflict   [:tab-state/id]
                 :do-update-set [:tab-state/data]}]
       :status 204})))

(def module
  {:biff.ring/routes
   ["" {:middleware [mid/wrap-signed-in]}
    [(routes/app)                {:get demo-page}]
    [(set-display-name-path)     {:post set-display-name}]
    [(set-background-color-path) {:post set-background-color}]]})
