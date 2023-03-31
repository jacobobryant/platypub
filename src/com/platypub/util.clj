(ns com.platypub.util
  (:require [buddy.core.mac :as mac]
            [clj-http.client :as http]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb :as biff :refer [q]]
            [lambdaisland.uri :as uri]
            [ring.util.io :as ring-io]
            [ring.util.mime-type :as mime]
            [ring.util.time :as ring-time]
            [xtdb.api :as xt]))

(defn add-prefix [prefix k]
  (keyword (str prefix (namespace k)) (name k)))

(defn join [sep xs]
  (rest (mapcat vector (repeat sep) xs)))

;; fix bug in select-ns-as

(defn ns-parts [nspace]
  (if (empty? (str nspace))
    []
    (str/split (str nspace) #"\.")))

(defn select-ns [m nspace]
  (let [parts (ns-parts nspace)]
    (->> (keys m)
         (filter (fn [k]
                   (= parts (take (count parts) (ns-parts (namespace k))))))
         (select-keys m))))

(defn select-ns-as [m ns-from ns-to]
  (->> (select-ns m ns-from)
       (map (fn [[k v]]
              (let [new-ns-parts (->> (ns-parts (namespace k))
                                      (drop (count (ns-parts ns-from)))
                                      (concat (ns-parts ns-to)))]
                [(if (empty? new-ns-parts)
                   (keyword (name k))
                   (keyword (str/join "." new-ns-parts) (name k)))
                 v])))
       (into {})))

;;;;

(defn dissoc-ns [m nspace]
  (let [parts (ns-parts nspace)]
    (->> (keys m)
         (remove (fn [k]
                   (= parts (take (count parts) (ns-parts (namespace k))))))
         (select-keys m))))

(defn rename-ns [m ns-from ns-to]
  (merge (dissoc-ns m ns-from)
         (select-ns-as m ns-from ns-to)))

(defn make-url [& args]
  (let [[args query] (if (map? (last args))
                       [(butlast args) (last args)]
                       [args {}])]
    (str (apply uri/assoc-query
                (str/replace (str "/" (str/join "/" args)) #"/+" "/")
                (apply concat query)))))

(defmacro else->> [& forms] `(->> ~@(reverse forms)))

(defn split-by [pred coll]
  [(remove pred coll)
   (filter pred coll)])

(defn sha-hex [file algo]
  (with-open [f (java.io.FileInputStream. file)]
    (let [buffer (byte-array 1024)
          md (java.security.MessageDigest/getInstance algo)]
      (loop [nread (.read f buffer)]
        (if (pos? nread)
          (do (.update md buffer 0 nread)
              (recur (.read f buffer)))
          (apply str (map #(format "%02x" (bit-and % 0xff)) (.digest md))))))))

(defn sha1-hex [file]
  (sha-hex file "SHA-1"))

(defn sha256-hex [file]
  (sha-hex file "SHA-256"))

(defn hmac-sha1-base64 [secret s]
  (-> (mac/hash s {:key secret :alg :hmac+sha1})
      biff/base64-encode))

(defn md5-base64 [file]
  (with-open [f (java.io.FileInputStream. file)]
    (let [buffer (byte-array 1024)
          md (java.security.MessageDigest/getInstance "MD5")]
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

(defn s3 [{:keys [biff/secret
                  s3/base-url
                  s3/bucket
                  s3/access-key]
           :as sys}
          {:keys [method
                  key
                  file
                  headers]}]
  ;; See https://docs.aws.amazon.com/AmazonS3/latest/userguide/RESTAuthentication.html
  ;; We should upgrade to v4 at some point, maybe.
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
        signature (hmac-sha1-base64 (secret :s3/secret-key) string-to-sign)
        auth (str "AWS " access-key ":" signature)]
    (http/request {:method method
                   :url (str base-url path)
                   :headers (merge {"Authorization" auth
                                    "Date" date
                                    "Content-MD5" md5}
                                   headers)
                   :body (some-> file file->bytes)})))

(defn resolve-theme [{:keys [com.platypub/themes]} theme-str]
  (some->> (map str themes)
           (some #{theme-str})
           symbol
           requiring-resolve
           deref))

(defn merge-site-config [ctx site]
  (merge site
         (select-ns-as
          (resolve-theme ctx (:site/theme site))
          nil
          'site.config)))

(defn q-sites [{:keys [biff/db] :as ctx} user]
  (->> (q db
          '{:find (pull site [*])
            :in [user]
            :where [[site :site/user user]]}
          (:xt/id user))
       (map #(merge-site-config ctx %))))

(defn serve-static-file [file]
  {:status 200
   :headers {"content-length" (str (.length file))
             "last-modified" (ring-time/format-date (ring-io/last-modified-date file))
             "content-type" (mime/ext-mime-type (.getName file))}
   :body file})

(defn q-items [db user site item-spec]
  (q db
     {:find '(pull item [*])
      :in '[user site]
      :where (->> (:query item-spec)
                  (map (fn [x]
                         (let [[k & rst] (if (keyword? x)
                                           [x]
                                           x)
                               k (keyword (str "item.custom." (namespace k)) (name k))]
                           (into ['item k] rst))))
                  (into '[[item :item/user user]
                          [item :item/sites site]]))}
     (:xt/id user)
     (:xt/id site)))

(defn something? [x]
  (if (or (coll? x) (string? x))
    (boolean (not-empty x))
    (some? x)))

(defn get-render-opts [{:keys [biff/secret biff/db user site item] :as sys}]
  (let [defaults (->> site
                      :site.config/fields
                      (map (fn [[k v]]
                             [k (:default v)]))
                      (into {}))
        site' (-> site
                  (dissoc-ns 'site.config)
                  (rename-ns 'site.custom nil))
        site' (reduce (fn [m k]
                        (if (something? (m k))
                          m
                          (assoc m k (defaults k))))
                      site'
                      (:site.config/site-fields site))]
    (into {:account (-> sys
                        (select-keys [:mailgun/domain :recaptcha/site-key])
                        (assoc :mailgun/api-key (secret :mailgun/api-key))
                        (assoc :recaptcha/secret (secret :recaptcha/secret-key))
                        (assoc :recaptcha/secret-key (secret :recaptcha/secret-key)))
           :site site'
           :lists (q db
                     '{:find (pull lst [*])
                       :in [user site]
                       :where [[lst :list/user user]
                               [lst :list/sites site]]}
                     (:xt/id user)
                     (:xt/id site))
           :item (rename-ns item 'item.custom nil)}
          (for [item-spec (:site.config/items site)]
            [(:key item-spec)
             (->> (q-items db user site item-spec)
                  (map #(rename-ns % 'item.custom nil)))]))))

(defn last-edited [db id]
  (:xtdb.api/valid-time (first (xt/entity-history db id :desc))))

(defn order-by-fn [order-by-spec]
  (let [order-by-spec (for [[k dir] order-by-spec]
                        [(add-prefix "item.custom." k) dir])]
    (fn [a b]
      (or (->> order-by-spec
               (map (fn [[k direction]]
                      (cond-> (compare (k a) (k b))
                        (= direction :desc) (* -1))))
               (remove zero?)
               first)
          0))))

(defn match? [spec item]
  (if (= :not (first spec))
    (not (match? (second spec) item))
    (every? (fn [[k v]]
              (= (get item (add-prefix "item.custom." k)) v))
            spec)))

(defn slugify [title]
  (-> title
      str/lower-case
      ; RFC 3986 reserved or unsafe characters in url
      (str/replace #"[/|]" "-")
      (str/replace #"[:?#\[\]@!$&'()*+,;=\"<>%{}\\^`]" "")
      (str/replace #"\s+" "-")))

(defn date-time-string->java-Date [date-string]
  (when (not-empty date-string)
    (edn/read-string (str "#inst" "\"" date-string "\""))))

(defn params->custom-fields [{:keys [site item-spec params] :as ctx}]
  (let [theme (resolve-theme ctx (:site/theme site))
        [prefix ks] (if item-spec
                      ["item.custom." (:fields item-spec)]
                      ["site.custom." (:site.config/site-fields site)])]
    (for [k ks
          :let [value (get params (keyword (name k)))
                {:keys [type default]} (get-in site [:site.config/fields k])]]
      [(add-prefix prefix k)
       (cond
         (= type :instant) (date-time-string->java-Date value)
         (= type :boolean) (= value "on")
         (= type :tags) (->> (str/split value #"\s+")
                             (remove empty?)
                             distinct
                             vec)
         (and (empty? value)
              (instance? clojure.lang.PersistentVector default))
         ((case (first default)
            :slugify slugify
            (constantly nil))
          (params (keyword (name (second default)))))

         :else value)])))
