(ns com.platypub.lib.request-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.lib.request :as request]))

(deftest value-test
  (is (= "upload"
         (request/value {:form-params {"request_image" "upload"}}
                        :request/image)))
  (is (= "upload"
         (request/value {:params {:request_image "upload"}}
                        :request/image)))
  (is (= "tab"
         (request/value
          {:form-params {"biff_datastar_client-tab-id" "tab"}}
          :biff.datastar/client-tab-id))))
