(ns com.platypub.lib.subscriber
  (:require [clojure.string :as str]))

(defn normalize-email [email]
  (some-> email str str/trim str/lower-case))

(defn valid-email? [email]
  (boolean (and email
                (re-matches #"(?i)[^\s@]+@[^\s@]+\.[^\s@]+" email))))
