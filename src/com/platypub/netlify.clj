(ns com.platypub.netlify
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [com.platypub.util :as util]))

(defn netlify [m]
  (-> (merge {:method :post
              :as :json} m)
      (update :url #(str "https://api.netlify.com/api/v1" %))
      http/request))

(defn deploy! [{:keys [api-key site-id dir]}]
  (let [path->file (->> (file-seq (io/file dir))
                        (filter #(.isFile %))
                        (map (fn [f]
                               [(subs (.getPath f) (count dir)) f]))
                        (into {}))
        path->sha1 (->> path->file
                        (map (fn [[path file]]
                               [path (util/sha1-hex file)]))
                        (into {}))
        {:keys [id required]} (:body
                                (netlify
                                  {:url (str "/sites/" site-id "/deploys")
                                   :oauth-token api-key
                                   :content-type :json
                                   :form-params {:files path->sha1}}))
        required (set required)]
    (println "Uploading" (count required) "files")
    (doseq [[path f] path->file
            :let [sha1 (path->sha1 path)]
            :when (required sha1)]
      (println path)
      (netlify {:method :put
                :oauth-token api-key
                :url (str "/deploys/" id "/files" path)
                :content-type :application/octet-stream
                :body (io/input-stream f)}))
    (println "Done.")))
