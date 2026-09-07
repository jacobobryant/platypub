;; A scratch space for inspecting things with the REPL.
(ns repl
  (:require [clojure.tools.namespace.repl :as tn-repl]
            [com.biffweb.sqlite :refer [execute]]
            [com.platypub :as main]))

(defn get-ctx []
  @main/system)

(defn refresh []
  (main/stop)
  (tn-repl/refresh :after `main/start)
  :done)

(comment

  ;; Most changes will be evaluated whenver you save a file. (refresh) is only
  ;; needed if you change code that only runs at startup, like database schema.
  (refresh)

  (execute (get-ctx) "select * from user"))
