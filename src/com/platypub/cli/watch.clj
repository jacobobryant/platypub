(ns com.platypub.cli.watch
  (:refer-clojure :exclude [future])
  (:require [babashka.process :as process]
            [clojure.stacktrace :as st]
            [com.platypub.reload :as reload]
            [com.platypub.util :as util]
            [nextjournal.beholder :as beholder]
            [nrepl.cmdline :as nrepl-cmd]))

(defmacro future [& body]
  `(clojure.core/future
     (try
       ~@body
       (catch Exception e#
         (binding [*err* *out*]
           (st/print-stack-trace e#))))))

;; https://gist.github.com/oliyh/0c1da9beab43766ae2a6abc9507e732a
(defn- debounce
  ([f] (debounce f 1000))
  ([f timeout]
   (let [timer (java.util.Timer.)
         task (atom nil)]
     (with-meta
      (fn [& args]
        (when-let [t ^java.util.TimerTask @task]
          (.cancel t))
        (let [new-task (proxy [java.util.TimerTask] []
                         (run []
                           (apply f args)
                           (reset! task nil)
                           (.purge timer)))]
          (reset! task new-task)
          (.schedule timer new-task timeout)))
      {:task-atom task}))))

(defn on-save []
  (try
    (let [config (util/read-config)]
      ((requiring-resolve (:site/theme config)) (merge config {:dev true})))
    (prn :ok)
    (catch Exception e
      (st/print-stack-trace e)
      (flush))))

(defn watch
  "Builds your site whenever a file changes. Also starts a Netlify dev server."
  []
  (process/shell "npm install")
  (future (process/shell "npx netlify dev"))
  (future (process/shell "npx tailwindcss -c resources/tailwind.config.js -i resources/tailwind.css -o public/css/main.css --watch"))
  (beholder/watch (debounce (fn [_]
                              (reload/reload ["src"])
                              (on-save))
                            50)
                  "src"
                  "resources"
                  "content")
  (on-save)
  (nrepl-cmd/-main "--middleware" "[cider.nrepl/cider-middleware]"))
