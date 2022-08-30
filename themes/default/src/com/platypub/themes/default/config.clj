(ns com.platypub.themes.default.config
  (:require [clojure.java.io :as io]))

(defn -main []
  (prn (slurp (io/resource "com/platypub/themes/default/config.edn"))))
