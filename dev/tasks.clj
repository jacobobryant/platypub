(ns tasks
  (:require [com.biffweb.run :as biff.run]
            [com.biffweb.tasks :as biff.tasks]))

(def tasks
  (merge biff.tasks/app-tasks
         {"review-lint"
          {:task 'tasks.review-lint/review-lint
           :doc  "Check Biff namespace and lib usage conventions."}}))

(defn -main [& args]
  (apply biff.run/main tasks args))
