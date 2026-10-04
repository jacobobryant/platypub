(ns com.platypub.fx-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.fx :as fx]))

(deftest digitalocean-object-store-endpoint
  (is (= "https://sfo3.digitaloceanspaces.com"
         (fx/object-store-endpoint
          "https://platypub.sfo3.digitaloceanspaces.com"
          "platypub")))
  (is (= "https://sfo3.digitaloceanspaces.com"
         (fx/object-store-endpoint
          "https://sfo3.digitaloceanspaces.com"
          "platypub")))
  (is (= "http://localhost:9000"
         (fx/object-store-endpoint "http://localhost:9000" "platypub")))
  (is (thrown? clojure.lang.ExceptionInfo
               (fx/object-store-endpoint
                "https://other.sfo3.digitaloceanspaces.com"
                "platypub"))))

(deftest low-level-handlers
  (let [state (atom {:emails []})]
    (is (= {:emails []} (fx/deref-atom {} state)))
    (is (= {:emails ["message"]}
           (fx/swap-atom {:platypub/state state}
                         :platypub/state
                         `update
                         :emails
                         `conj
                         "message")))
    (is (= {:emails []} (fx/reset-atom {} state {:emails []}))))
  (is (= "hello" (fx/read-uploaded-file {} (.getBytes "hello")))))

(deftest uploaded-object-access
  (let [make-args    @#'fx/put-object-args
        data         (.getBytes "image")
        private      (make-args "bucket"
                                {:key "private" :content-type "image/png"}
                                data true)
        public-opts  {:key          "public"
                      :content-type "image/png"
                      :headers      {"x-amz-acl" "public-read"}}
        public-prod  (make-args "bucket" public-opts data false)
        public-local (make-args "bucket" public-opts data true)]
    (is (empty? (.get (.headers private) "x-amz-acl")))
    (is (= ["public-read"]
           (vec (.get (.headers public-prod) "x-amz-acl"))))
    (is (= ["public-read"]
           (vec (.get (.headers public-local) "x-amz-acl"))))
    (is (= ["public-read"]
           (vec (.get (.userMetadata public-local)
                      "x-amz-meta-platypub-access"))))
    (is (empty? (.get (.userMetadata public-prod)
                      "x-amz-meta-platypub-access")))))

(deftest module-only-exposes-low-level-effects
  (let [handlers (:biff.fx/handlers fx/module)]
    (is (every? (set (keys handlers))
                [:biff.fx/sleep
                 :platypub.fx/get-object
                 :platypub.fx/put-object
                 :platypub.fx/swap!
                 :platypub.fx/print]))
    (is (not (contains? handlers :platypub.fx/get-mock-public-object)))
    (is (not (contains? handlers :platypub.fx/put-public-object)))))
