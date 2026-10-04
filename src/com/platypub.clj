(ns com.platypub
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.datastar :refer [disconnect]]
            [com.platypub.modules :refer [modules start-order]]
            [nrepl.cmdline :as nrepl])
  (:gen-class))

(defonce system (atom {}))
(disconnect @system)

(defn start []
  (reset! system (biff.core/start #'modules start-order)))

(defn stop []
  (biff.core/stop @system)
  (reset! system {})
  :stopped)

(defn -main [& _args]
  (let [{:biff.tasks/keys [nrepl-port]} (start)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #'stop))
    (nrepl/-main "--port" nrepl-port
                 "--middleware" (pr-str '[cider.nrepl/cider-middleware]))))
