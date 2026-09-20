(ns com.platypub.routes
  (:require [com.biffweb.ring :refer [defpath]]))

(defpath app "/app")
(defpath app-admin "/app/admin")
(defpath signin "/signin")
(defpath signout "/_biff/auth/signout")
(defpath publication "/app/publications/:id")
(defpath publication-settings "/app/publications/:id/settings")
(defpath publication-subscribers "/app/publications/:id/subscribers")
(defpath publication-send "/app/publications/:id/send")
(defpath subscribe "/subscribe/:id")
(defpath confirm-subscription "/confirm/:token")
(defpath unsubscribe "/unsubscribe/:token")
