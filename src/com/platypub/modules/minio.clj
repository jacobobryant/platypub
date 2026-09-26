(ns com.platypub.modules.minio
  (:require [clojure.tools.logging :as log]))

(defn start
  [system]
  (if-not (:platypub/local-minio-enabled system)
    system
    (let [command  (or (:platypub/local-minio-command system) "minio")
          data-dir (or (:platypub/local-minio-data-dir system)
                       "storage/minio")
          port     (or (:platypub/local-minio-port system) 9000)
          process  (-> (ProcessBuilder.
                        ^java.util.List
                        [command "server" data-dir "--address" (str ":" port)])
                       (.inheritIO)
                       .start)]
      (log/info "Started local MinIO on port" port)
      (assoc system :platypub/local-minio-process process))))

(defn stop
  [{:keys [platypub/local-minio-process]}]
  (when local-minio-process
    (.destroy ^Process local-minio-process)))

(def module
  {:biff.core/id    :platypub/local-minio
   :biff.core/start start
   :biff.core/stop  stop})
