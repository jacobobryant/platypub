(ns com.platypub.lib.tokens
  (:require [cheshire.core :as json]
            [clojure.string :as str])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]
           [java.util Base64]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

(defn- encode [^bytes value]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) value))

(defn- decode [value]
  (.decode (Base64/getUrlDecoder) ^String value))

(defn- hmac [secret value]
  (let [mac (Mac/getInstance "HmacSHA256")]
    (.init mac (SecretKeySpec. (.getBytes (str secret) StandardCharsets/UTF_8)
                               "HmacSHA256"))
    (.doFinal mac (.getBytes value StandardCharsets/UTF_8))))

(def jwt-header (encode (.getBytes (json/generate-string {:alg "HS256"
                                                          :typ "JWT"})
                                   StandardCharsets/UTF_8)))

(defn- sign
  [secret claims]
  (let [payload (encode (.getBytes (json/generate-string claims)
                                   StandardCharsets/UTF_8))
        signed  (str jwt-header "." payload)]
    (str signed "." (encode (hmac secret signed)))))

(defn- unsign
  [secret token]
  (try
    (let [[header payload signature & more] (str/split token #"\.")
          signed                            (str header "." payload)]
      (when (and (= jwt-header header) payload signature (empty? more)
                 (MessageDigest/isEqual (hmac secret signed)
                                        (decode signature)))
        (json/parse-string
         (String. (decode payload) StandardCharsets/UTF_8)
         true)))
    (catch Exception _ nil)))

(defn process
  [operation secret value]
  (case operation
    :sign (sign secret value)
    :unsign (unsign secret value)))
