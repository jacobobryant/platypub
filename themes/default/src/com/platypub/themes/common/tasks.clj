(ns com.platypub.themes.common.tasks
  (:require [babashka.tasks :refer [shell]]
            [babashka.fs :as fs]
            [babashka.nrepl.server :as nrepl]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]))

(defn start-nrepl []
  (let [port (+ 1023 (rand-int 10000))]
    (nrepl/start-server! {:host "localhost"
                          :port port})
    (spit ".nrepl-port" (str port))
    (-> (Runtime/getRuntime)
        (.addShutdownHook
         (Thread. (fn [] (fs/delete ".nrepl-port")))))))

(defn dev []
  (start-nrepl)
  (when (fs/exists? "package.json")
    (shell "npm install"))
  (future
   (shell "npx chokidar **/* --initial"
          "-i" "public"
          "-i" "netlify"
          "-i" ".netlify"
          "-i" "node_modules"
          "-i" "main.css"
          "-i" "config.edn"
          "-c" "bb -on-save"))
  (shell "netlify dev -d public"))

(defn clean []
  (fs/delete-tree "public"))

(defn css []
  (shell "npx tailwindcss"
         "-c" "tailwind.config.js"
         "-i" "tailwind.css"
         "-o" "main.css"
         "--minify")
  (io/make-parents "public/css/_")
  (fs/copy "main.css" "public/css/main.css" {:replace-existing true}))

(defn -on-save []
  (spit "config.edn" (:out (shell/sh "bb" "config")))
  (clean)
  (shell "bb run render-site")
  (css))

(defn build []
  (when (fs/exists? "package.json")
    (shell "npm install"))
  (-on-save))
