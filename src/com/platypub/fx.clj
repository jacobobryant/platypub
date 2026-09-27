(ns com.platypub.fx
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import [io.minio BucketExistsArgs GetObjectArgs MakeBucketArgs MinioClient
            PutObjectArgs]
           [io.minio.errors ErrorResponseException]
           [java.io ByteArrayInputStream InputStream]
           [java.nio.charset StandardCharsets]
           [java.nio.file Path]))

(defn- file-value-bytes
  [value]
  (cond
    (instance? (Class/forName "[B") value) value

    (instance? java.io.File value)
    (with-open [input (io/input-stream value)] (.readAllBytes input))

    (instance? Path value)
    (with-open [input (io/input-stream (.toFile ^Path value))]
      (.readAllBytes input))

    (instance? InputStream value)
    (with-open [input value] (.readAllBytes input))

    :else (throw (ex-info "Unsupported file value."
                          {:class (some-> value class str)}))))

(defn- object-bytes
  [value]
  (if (string? value)
    (.getBytes ^String value StandardCharsets/UTF_8)
    (file-value-bytes value)))

(defn object-store-endpoint
  [endpoint bucket]
  (if-let [[_ endpoint-bucket region]
           (re-matches
            #"https://([^.]+)\.([^.]+)\.digitaloceanspaces\.com/?"
            endpoint)]
    (if (= endpoint-bucket bucket)
      (str "https://" region ".digitaloceanspaces.com")
      (throw (ex-info "Object store endpoint names a different bucket."
                      {:endpoint endpoint :bucket bucket})))
    endpoint))

(defn- object-store-client
  [{:keys                       [platypub/object-store-client]
    :platypub.object-store/keys [endpoint bucket access-key secret-key]}]
  (or @object-store-client
      (let [new-client (-> (MinioClient/builder)
                           (.endpoint (object-store-endpoint endpoint bucket))
                           (.credentials
                            ;; access-key should not be a secret. in config.edn
                            ;; use #biff/env instead of #biff/secret, and remove
                            ;; the force here.
                            (force access-key)
                            (force secret-key))
                           .build)]
        (or (swap! object-store-client #(or % new-client)) new-client))))

(defn- ensure-bucket
  [client bucket]
  (when-not (.bucketExists client
                           (-> (BucketExistsArgs/builder)
                               (.bucket bucket)
                               .build))
    (.makeBucket client
                 (-> (MakeBucketArgs/builder)
                     (.bucket bucket)
                     .build))))

(defn put-object
  [ctx object-key value content-type]
  (let [client (object-store-client ctx)
        bucket (:platypub.object-store/bucket ctx)
        data   (object-bytes value)]
    (try
      (ensure-bucket client bucket)
      (.putObject client
                  (-> (PutObjectArgs/builder)
                      (.bucket bucket)
                      (.object (str object-key))
                      (.contentType
                       (or content-type "application/octet-stream"))
                      (.stream (ByteArrayInputStream. data) (alength data) -1)
                      .build))
      (catch ErrorResponseException e
        (throw (ex-info
                (str "Object upload failed: " (.code (.errorResponse e))
                     " (HTTP " (.code (.response e)) ")")
                {:bucket bucket :object-key (str object-key)}
                e))))
    object-key))

(defn get-object
  [ctx object-key]
  (with-open [input (.getObject
                     (object-store-client ctx)
                     (-> (GetObjectArgs/builder)
                         (.bucket (:platypub.object-store/bucket ctx))
                         (.object (str object-key))
                         .build))]
    (.readAllBytes input)))

(defn reset-atom
  [_ctx state value]
  (reset! state value))

(defn swap-atom
  [ctx state-key f & args]
  (let [state (get ctx state-key)
        f     (if (qualified-symbol? f) (requiring-resolve f) f)
        args  (mapv #(if (qualified-symbol? %) (requiring-resolve %) %) args)]
    (when-not (instance? clojure.lang.IAtom state)
      (throw (ex-info "System value is not an atom." {:key state-key})))
    (apply swap! state f args)))

(defn deref-atom
  [_ctx state]
  @state)

(defn sleep
  [_ctx milliseconds]
  (Thread/sleep milliseconds))

(defn print-value
  [_ctx value]
  (println value))

(defn http-with-backoff
  [_ctx request]
  (let [request* (requiring-resolve 'hato.client/request)]
    (loop [attempt 0]
      (let [response (request* request)]
        (if (and (= 429 (:status response)) (< attempt 5))
          (let [retry-after (some-> (get-in response [:headers "retry-after"])
                                    parse-long)

                delay-ms
                (* 1000 (or retry-after (bit-shift-left 1 attempt)))]
            (Thread/sleep delay-ms)
            (recur (inc attempt)))
          (do
            (when (and (:status response) (<= 400 (:status response)))
              (log/error "Request failed" (:status response)))
            response))))))

(defn read-uploaded-file
  [_ctx value]
  (String. ^bytes (file-value-bytes value) StandardCharsets/UTF_8))

(def module
  {:biff.core/init {:platypub/object-store-client (atom nil)}

   :biff.fx/handlers
   {:biff.fx/sleep                 sleep
    :platypub.fx/get-object        get-object
    :platypub.fx/http-with-backoff http-with-backoff
    :platypub.fx/print             print-value
    :platypub.fx/put-object        put-object
    :platypub.fx/swap!             swap-atom
    :platypub/deref                deref-atom
    :platypub/read-uploaded-file   read-uploaded-file
    :platypub/reset-atom           reset-atom}})
