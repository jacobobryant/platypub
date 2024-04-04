(ns com.platypub.cli.publish
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [com.platypub.util :as util]))

(defn publish []
  (fs/delete-tree "public")
  (let [config (util/read-config)]
    (process/shell "npx tailwindcss -c resources/tailwind.config.js -i resources/tailwind.css -o public/css/main.css --minify")
    ((requiring-resolve (:site/theme config)) config)
    (process/shell "npx netlify deploy --prod")))
