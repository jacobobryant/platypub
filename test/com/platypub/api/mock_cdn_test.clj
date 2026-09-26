(ns com.platypub.api.mock-cdn-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.api.mock-cdn :as mock-cdn]))

(deftest serve-object-states-test
  (let [[load-object respond] (mock-cdn/serve-object)]
    (is (= {:biff.fx/return {:status 404}}
           (load-object {:platypub/local-minio-enabled false})))
    (let [loaded (load-object
                  {:platypub/local-minio-enabled true
                   :path-params                  {:object-key "image.png"}})
          body   (byte-array [1 2 3])]
      (is (= [:platypub.fx/get-object "image.png"] (:body loaded)))
      (is (= {:status  200
              :headers {"Content-Type" "image/png"

                        "Cache-Control"
                        "public, max-age=31536000, immutable"}
              :body    body}
             (respond {} (assoc loaded :body body)))))))
