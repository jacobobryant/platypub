(ns com.platypub.netlify
  (:require [com.biffweb :as biff]
            [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.platypub.util :as util]))

(def base-url "https://api.netlify.com/api/v1")

(defn deploy! [{:keys [api-key site-id dir draft]}]
  (when (.exists (io/file dir "netlify/functions"))
    (println "Installing NPM deps...")
    (biff/sh "npm" "install" :dir dir)
    (println "Preparing functions...")
    (biff/sh "npx" "zip-it-and-ship-it" "netlify/functions" "functions-dist" :dir dir))
  (let [path->file (->> (file-seq (io/file dir "public"))
                        (filter #(.isFile %))
                        (map (fn [f]
                               [(subs (.getPath f) (count (str dir "/public"))) f]))
                        (into {}))
        path->sha1 (update-vals path->file util/sha1-hex)
        fn->file (->> (file-seq (io/file dir "functions-dist"))
                      (filter #(.isFile %))
                      (map (fn [f]
                             [(-> (.getPath f)
                                  (str/replace #".*/" "")
                                  (str/replace #"\.zip$" ""))
                              f]))
                      (into {}))
        fn->sha256 (update-vals fn->file util/sha256-hex)
        {:keys [id required required_functions]
         :as response} (:body
                        (http/post (str base-url "/sites/" site-id "/deploys")
                                   {:oauth-token api-key
                                    :content-type :json
                                    :form-params {:files path->sha1
                                                  :functions fn->sha256
                                                  :draft draft}
                                    :as :json}))
        required (set required)
        required-fn (set required_functions)]
    (println "Uploading" (count required) "files and" (count required-fn) "functions")
    (doseq [[path f] path->file
            :let [sha1 (path->sha1 path)]
            :when (required sha1)]
      (println path)
      (http/put (str base-url "/deploys/" id "/files" path)
                {:oauth-token api-key
                 :content-type :application/octet-stream
                 :body (io/input-stream f)}))
    (doseq [[fn-name f] fn->file
            :let [sha1 (fn->sha256 fn-name)]
            :when (required-fn sha1)]
      (println fn-name)
      (http/put (str base-url "/deploys/" id "/functions/" fn-name)
                {:query-params {:runtime "js"}
                 :oauth-token api-key
                 :content-type :application/octet-stream
                 :body (io/input-stream f)}))
    (println "Done.")
    response))

(defn create! [{:keys [biff/secret]}]
  (http/post (str base-url "/sites")
             {:oauth-token (secret :netlify/api-key)
              :as :json}))

(defn delete! [{:keys [biff/secret]} {:keys [site-id]}]
  (http/delete (str base-url "/sites/" site-id)
               {:oauth-token (secret :netlify/api-key)
                :as :json}))
