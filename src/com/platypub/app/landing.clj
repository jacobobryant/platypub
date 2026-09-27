(ns com.platypub.app.landing
  (:require [com.biffweb.ring :refer [defpath]]
            [com.platypub.routes :as routes]))

(defpath root-path "")
(defpath home-path "/")

(defn- wrap-root-redirect
  [handler]
  (fn [{:keys [session], :as ctx}]
    (handler
     (assoc ctx
            :root/location
            (if (some? (:uid session)) (routes/app) (routes/signin))))))

(defn home [{:root/keys [location]}]
  {:status 303, :headers {"location" location}})

(def module
  {:biff.ring/routes
   [(root-path)
    {:middleware [wrap-root-redirect]}
    [(home-path) {:get home}]]})
