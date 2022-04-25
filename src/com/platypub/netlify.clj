(ns com.platypub.netlify
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]))

(defn sha1
  "Compute the SHA-1 of a File's contents and return the hex string"
  [file]
  (with-open [f (java.io.FileInputStream. file)]
    (let [buffer (byte-array 1024)
          md (java.security.MessageDigest/getInstance "SHA-1") ]
      (loop [nread (.read f buffer)]
        (if (pos? nread)
          (do (.update md buffer 0 nread)
              (recur (.read f buffer)))
          (format "%040x" (BigInteger. 1 (.digest md)) 16))))))

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
                               [path (sha1 file)]))
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
