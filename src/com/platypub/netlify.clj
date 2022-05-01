(ns com.platypub.netlify
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [com.platypub.util :as util]))

(def base-url "https://api.netlify.com/api/v1")

(defn deploy! [{:keys [api-key site-id dir draft]}]
  (let [path->file (->> (file-seq (io/file dir))
                        (filter #(.isFile %))
                        (map (fn [f]
                               [(subs (.getPath f) (count dir)) f]))
                        (into {}))
        path->sha1 (->> path->file
                        (map (fn [[path file]]
                               [path (util/sha1-hex file)]))
                        (into {}))
        {:keys [id required]
         :as response} (:body
                         (http/post (str base-url "/sites/" site-id "/deploys")
                                    {:oauth-token api-key
                                     :content-type :json
                                     :form-params {:files path->sha1
                                                   :draft draft}
                                     :as :json}))
        required (set required)]
    (println "Uploading" (count required) "files")
    (doseq [[path f] path->file
            :let [sha1 (path->sha1 path)]
            :when (required sha1)]
      (println path)
      (http/put (str base-url "/deploys/" id "/files" path)
                {:oauth-token api-key
                 :content-type :application/octet-stream
                 :body (io/input-stream f)}))
    (println "Done.")
    response))

(defn create! [{:keys [netlify/api-key]}]
  (http/post (str base-url "/sites")
             {:oauth-token api-key
              :as :json}))

(defn delete! [{:keys [netlify/api-key]} {:keys [site-id]}]
  (http/delete (str base-url "/sites/" site-id)
               {:oauth-token api-key
                :as :json}))
