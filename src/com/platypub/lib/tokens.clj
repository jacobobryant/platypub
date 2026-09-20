(ns com.platypub.lib.tokens
  (:require [clojure.string :as str])
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

(defn- sign
  [secret claims]
  (let [payload (encode (.getBytes (pr-str claims) StandardCharsets/UTF_8))]
    (str payload "." (encode (hmac secret payload)))))

(defn- unsign
  [secret token]
  (try
    (let [[payload signature & more] (str/split token #"\.")]
      (when (and payload signature (empty? more)
                 (MessageDigest/isEqual (hmac secret payload)
                                        (decode signature)))
        (read-string (String. (decode payload) StandardCharsets/UTF_8))))
    (catch Exception _ nil)))

(defn process
  [operation secret value]
  (case operation
    :sign (sign secret value)
    :unsign (unsign secret value)))
