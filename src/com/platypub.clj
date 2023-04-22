(ns com.platypub
  (:require [com.biffweb :as biff]
            [com.platypub.email :as email]
            [com.platypub.home :as home]
            [com.platypub.lists :as lists]
            [com.platypub.items :as items]
            [com.platypub.sites :as sites]
            [com.platypub.schema :as schema]
            [com.platypub.util :as util]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :as tn-repl]
            [malli.core :as malc]
            [malli.registry :as malr]
            [ring.middleware.anti-forgery :as anti-forgery]
            [nrepl.cmdline :as nrepl-cmd]))

(defn email-valid? [{:keys [com.platypub/allowed-users]} email]
  (if allowed-users
    (contains? allowed-users email)
    (and email
         (re-matches #".+@.+\..+" email)
         (not (re-find #"\s" email)))))

(def plugins
  [(biff/authentication-plugin {:biff.auth/email-validator email-valid?})
   home/plugin
   lists/plugin
   items/plugin
   schema/plugin
   sites/plugin])

(def routes [["" {:middleware [biff/wrap-site-defaults]}
              (keep :routes plugins)]
             ["" {:middleware [biff/wrap-api-defaults]}
              (keep :api-routes plugins)]])

(def handler (-> (biff/reitit-handler {:routes routes})
                 biff/wrap-base-defaults))

(def static-pages (apply biff/safe-merge (map :static plugins)))

(defn generate-assets! [sys]
  (when (:com.platypub/enable-web sys)
    (biff/export-rum static-pages "target/resources/public")
    (biff/delete-old-files {:dir "target/resources/public"
                            :exts [".html"]})))

(defn on-save [sys]
  (biff/add-libs)
  (biff/eval-files! sys)
  (generate-assets! sys)
  (reset! (:com.platypub/code-last-modified sys) (java.util.Date.))
  (log/info :done)
  ;; Uncomment this if we add any real tests.
  #_(test/run-all-tests #"com.platypub.test.*"))

(def malli-opts
  {:registry (malr/composite-registry
              malc/default-registry
              (apply biff/safe-merge
                     (keep :schema plugins)))})

(def components
  [biff/use-config
   biff/use-secrets
   biff/use-xt
   biff/use-queues
   biff/use-tx-listener
   biff/use-jetty
   biff/use-chime
   biff/use-beholder])

(def initial-system
  {:com.platypub/code-last-modified (atom (java.util.Date.))
   :biff/send-email #'email/send-email
   :biff/plugins #'plugins
   :biff/tx-fns biff/tx-fns
   :biff/handler #'handler
   :biff/malli-opts #'malli-opts
   :biff.beholder/on-save #'on-save})

(defonce system (atom {}))

(defn start []
  (let [new-system (reduce (fn [system component]
                             (log/info "starting:" (str component))
                             (component system))
                           initial-system
                           components)]
    (reset! system new-system)
    (generate-assets! new-system)
    (log/info "Go to" (:biff/base-url new-system))))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))

(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (reset! system {})
  (tn-repl/refresh :after `start))

(comment
  ;; Evaluate this if you make a change to initial-system, components, :tasks,
  ;; or :queues.
  (refresh)

  ;; If that messes up your editor's REPL integration, you may need to use this
  ;; instead:
  (biff/fix-print (refresh)))
