(ns com.platypub.routes
  (:require [com.biffweb.ring :refer [defpath]]))

(defpath app "/app")
(defpath app-admin "/app/admin")
(defpath signin "/signin")
(defpath signout "/_biff/auth/signout")
(defpath publication "/app/publications/:publication-id")
(defpath archived-publications "/app/archived-publications")
(defpath archive-publication "/app/publications/:publication-id/archive")
(defpath unarchive-publication "/app/publications/:publication-id/unarchive")
(defpath publication-settings "/app/publications/:publication-id/settings")
(defpath publication-subscribers
  "/app/publications/:publication-id/subscribers")
(defpath publication-send "/app/publications/:publication-id/send")
(defpath subscribe "/subscribe/:publication-id")
(defpath confirm-subscription "/confirm/:token")
(defpath unsubscribe "/unsubscribe/:token")
