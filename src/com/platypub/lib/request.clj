(ns com.platypub.lib.request
  (:import [java.util UUID]))

(defn value
  [ctx attribute]
  (let [signals    (:biff.datastar/signals ctx)
        candidates [attribute (name attribute) (str attribute)]]
    (or (some #(when (contains? signals %) (get signals %)) candidates)
        (some (fn [params]
                (some #(when (contains? params %) (get params %)) candidates))
              (map ctx [:path-params
                        :query-params
                        :form-params
                        :body-params
                        :json-params
                        :params])))))

(defn signal-present?
  [ctx attribute]
  (contains? (:biff.datastar/signals ctx) attribute))

(defn uuid
  [value]
  (cond
    (uuid? value) value
    (string? value) (try
                      (UUID/fromString value)
                      (catch IllegalArgumentException _ nil))
    :else nil))

(defn path-uuid
  [ctx attribute]
  (uuid (value ctx attribute)))

(defn text
  [value]
  (when (string? value)
    value))
