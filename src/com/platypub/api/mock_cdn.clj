(ns com.platypub.api.mock-cdn
  (:require [clojure.string :as str]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]))

(defpath object-path "/_mock/cdn/:object-key")

(defn- content-type
  [object-key]
  (cond
    (str/ends-with? object-key ".png") "image/png"
    (or (str/ends-with? object-key ".jpg")
        (str/ends-with? object-key ".jpeg")) "image/jpeg"
    (str/ends-with? object-key ".gif") "image/gif"
    (str/ends-with? object-key ".webp") "image/webp"
    :else "application/octet-stream"))

(defpipeline serve-object
  (fn [{:keys [path-params platypub/local-minio-enabled]}]
    (if local-minio-enabled
      (let [object-key (:object-key path-params)]
        {:object-key object-key
         :body       [:platypub.fx/get-object object-key]})
      {:biff.fx/return {:status 404}}))

  (fn [_ctx {:keys [body object-key]}]
    {:status  200
     :headers {"Content-Type"  (content-type object-key)
               "Cache-Control" "public, max-age=31536000, immutable"}
     :body    body}))

(def module
  {:biff.ring/api-routes [[(object-path) {:get serve-object}]]})
