(ns com.platypub.util
  (:require [buddy.core.mac :as mac]
            [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb :as biff]
            [lambdaisland.uri :as uri]
            [ring.util.io :as ring-io]
            [ring.util.mime-type :as mime]
            [ring.util.time :as ring-time]))

(defn split-by [pred coll]
  [(remove pred coll)
   (filter pred coll)])

(defn sha1-hex [file]
  (with-open [f (java.io.FileInputStream. file)]
    (let [buffer (byte-array 1024)
          md (java.security.MessageDigest/getInstance "SHA-1") ]
      (loop [nread (.read f buffer)]
        (if (pos? nread)
          (do (.update md buffer 0 nread)
              (recur (.read f buffer)))
          (format "%040x" (BigInteger. 1 (.digest md)) 16))))))

(defn hmac-sha1-base64 [secret s]
  (-> (mac/hash s {:key secret :alg :hmac+sha1})
      biff/base64-encode))

(defn md5-base64 [file]
  (with-open [f (java.io.FileInputStream. file)]
    (let [buffer (byte-array 1024)
          md (java.security.MessageDigest/getInstance "MD5") ]
      (loop [nread (.read f buffer)]
        (if (pos? nread)
          (do (.update md buffer 0 nread)
              (recur (.read f buffer)))
          (biff/base64-encode (.digest md)))))))

(defn format-date [date & [format]]
  (.format (doto (new java.text.SimpleDateFormat (or format biff/rfc3339))
             (.setTimeZone (java.util.TimeZone/getTimeZone "UTC")))
           date))

(defn file->bytes [file]
  (let [out (byte-array (.length file))]
    (with-open [in (java.io.FileInputStream. file)]
      (.read in out)
      out)))

(defn s3 [{:keys [s3/base-url
                  s3/bucket
                  s3/access-key
                  s3/secret-key]}
          {:keys [method
                  key
                  file
                  headers]}]
  ;; See https://docs.aws.amazon.com/AmazonS3/latest/userguide/RESTAuthentication.html
  ;; We should upgrade to v4 at some point.
  (let [date (format-date (java.util.Date.) "EEE, dd MMM yyyy HH:mm:ss Z")
        path (str "/" bucket "/" key)
        md5 (some-> file md5-base64)
        headers' (->> headers
                      (map (fn [[k v]]
                             [(str/trim (str/lower-case k)) (str/trim v)]))
                      (into {}))
        content-type (get headers' "content-type")
        headers' (->> headers'
                      (filter (fn [[k v]]
                                (str/starts-with? k "x-amz-")))
                      (sort-by first)
                      (map (fn [[k v]]
                             (str k ":" v "\n")))
                      (apply str))
        string-to-sign (str method "\n" md5 "\n" content-type "\n" date "\n" headers' path)
        signature (hmac-sha1-base64 secret-key string-to-sign)
        auth (str "AWS " access-key ":" signature)]
    (http/request {:method method
                   :url (str base-url path)
                   :headers (merge {"Authorization" auth
                                    "Date" date
                                    "Content-MD5" md5}
                                   headers)
                   :body (some-> file file->bytes)})))

(defn wrap-signed-in [handler]
  (fn [{:keys [session] :as req}]
    (if (some? (:uid session))
      (handler req)
      {:status 303
       :headers {"location" "/"}})))

(defn serve-static-file [file]
  {:status 200
   :headers {"content-length" (str (.length file))
             "last-modified" (ring-time/format-date (ring-io/last-modified-date file))
             "content-type" (mime/ext-mime-type (.getName file))}
   :body file})
