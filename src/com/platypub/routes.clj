(ns com.platypub.routes
  (:require [com.biffweb.ring :refer [defpath]]))

(defpath home "/")
(defpath app "/app")
(defpath admin "/_biff/admin")
(defpath signin "/signin")
(defpath signout "/_biff/auth/signout")
