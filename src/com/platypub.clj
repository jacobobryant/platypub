(ns com.platypub
  (:require [com.biffweb :as biff]
            [com.platypub.feat.auth :as auth]
            [com.platypub.feat.home :as home]
            [com.platypub.feat.lists :as lists]
            [com.platypub.feat.posts :as posts]
            [com.platypub.feat.sites :as sites]
            [com.platypub.schema :refer [malli-opts]]
            [com.platypub.util :as util]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [ring.middleware.anti-forgery :as anti-forgery]
            [nrepl.cmdline :as nrepl-cmd]))

(def features
  [auth/features
   home/features
   lists/features
   posts/features
   sites/features])

(def routes [["" {:middleware [anti-forgery/wrap-anti-forgery
                               biff/wrap-anti-forgery-websockets
                               biff/wrap-render-rum]}
              (map :routes features)]
             (map :api-routes features)])

(def handler (-> (biff/reitit-handler {:routes routes})
                 (biff/wrap-inner-defaults {})))

(defn on-tx [sys tx]
  (let [sys (biff/assoc-db sys)]
    (doseq [{:keys [on-tx]} features
            :when on-tx]
      (on-tx sys tx))))

(def tasks (->> features
                (mapcat :tasks)
                (map #(update % :task comp biff/assoc-db))))

(def static-pages (apply biff/safe-merge (map :static features)))

(defn generate-assets! [sys]
  (when (:com.platypub/enable-web sys)
    (biff/export-rum static-pages "target/resources/public")
    (->> (file-seq (io/file "target/resources/public"))
         (filter (fn [file]
                   (and (.isFile file)
                        (biff/elapsed? (java.util.Date. (.lastModified file))
                                       :now
                                       30
                                       :seconds)
                        (str/ends-with? (.getPath file) ".html"))))
         (run! (fn [f]
                 (log/info "deleting" f)
                 (io/delete-file f))))
    (log/info "Generating CSS...")
    ;; Normally I'd use biff/sh which throws an exception + prints stderr when
    ;; the command fails, but tailwind returns status 0 even if there's an
    ;; error 😡
    (print (:err (shell/sh "npx" "tailwindcss"
                           "-c" "resources/tailwind.config.js"
                           "-i" "resources/tailwind.css"
                           "-o" "target/resources/public/css/main.css"
                           "--minify")))
    (log/info "CSS done")))

(defn on-save [sys]
  (biff/eval-files! sys)
  (println :done)
  (generate-assets! sys)
  ;; Uncomment this if we add any real tests.
  #_(test/run-all-tests #"com.platypub.test.*"))

(defn start []
  (biff/start-system
    {:com.platypub/chat-clients (atom #{})
     :biff/after-refresh `start
     :biff/handler #'handler
     :biff/malli-opts #'malli-opts
     :biff.hawk/on-save #'on-save
     :biff.xtdb/on-tx #'on-tx
     :biff.chime/tasks tasks
     :biff/config "config.edn"
     :biff/components [biff/use-config
                       biff/use-random-default-secrets
                       biff/use-xt
                       biff/use-tx-listener
                       (biff/use-when
                         :com.platypub/enable-web
                         biff/use-outer-default-middleware
                         biff/use-jetty)
                       (biff/use-when
                         :com.platypub/enable-worker
                         biff/use-chime)
                       (biff/use-when
                         :com.platypub/enable-hawk
                         biff/use-hawk)]})
  (generate-assets! @biff/system)
  (log/info "Go to" (:biff/base-url @biff/system)))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))
