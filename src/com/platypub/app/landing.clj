(ns com.platypub.app.landing
  (:require [com.platypub.lib.middleware :as mid]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]))

(defn home [_]
  (ui/page
   {}
   [:main {:class ["grid min-h-full flex-1 grid-rows-[1fr_auto_2fr]"
                   "justify-items-center"]}
    [:a {:class ["row-start-2 rounded bg-blue-600 px-4 py-2 text-white"]
         :href  (routes/signin)}
     "Click here to sign in."]]))

(def module
  {:biff.ring/routes
   ["" {:middleware [mid/wrap-redirect-signed-in]}
    [(routes/home) {:get home}]]})
