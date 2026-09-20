(ns com.platypub.app.landing
  (:require [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]))

(defpath root-path "")
(defpath home-path "/")

(defn- wrap-redirect-signed-in
  [handler]
  (fn [{:keys [session], :as ctx}]
    (if (some? (:uid session))
      {:status 303, :headers {"location" (routes/app)}}
      (handler ctx))))

(defn home [_]
  (ui/page
   {}
   [:main
    {:class ["grid min-h-full flex-1 grid-rows-[1fr_auto_2fr]"
             "justify-items-center"]}
    [:a
     {:class ["row-start-2 rounded bg-blue-600 px-4 py-2 text-white"]
      :href  (routes/signin)}
     "Click here to sign in."]]))

(def module
  {:biff.ring/routes
   [(root-path)
    {:middleware [wrap-redirect-signed-in]}
    [(home-path) {:get home}]]})
