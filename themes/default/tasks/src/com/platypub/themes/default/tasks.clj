(ns com.platypub.themes.default.tasks
  (:require [babashka.tasks :refer [shell]]))

(defn css* [& args]
  (apply shell
         "tailwindcss"
         "-c" "tailwind.config.js"
         "-i" "tailwind.css"
         "-o" "resources/com/platypub/themes/default/public/css/main.css"
         args))

(defn css []
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn []
              (println "Generating minified CSS...")
              (try
               ;; This generates an IllegalStateException, however the shell
               ;; command does successfully run in the background.
               (css* "--minify")
               (catch Exception e
                 nil)))))
  (css* "--watch"))
