(ns com.platypub.model.feed-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.model.feed :as feed]
            [com.platypub.test-helpers :as helpers]))

(deftest database-resolvers-test
  (is (= {:feed/id 1}
         (helpers/resolve-sql feed/by-url
                              {:feed/url "https://feed"}
                              [{:feed/id 1}])))
  (is (= {:feed/publications [{:publication/id 2}]}
         (helpers/resolve-sql feed/publications
                              {:feed/id 1}
                              [{:publication/id 2}])))
  (is (= {:feed/posts [{:post/id 2}]}
         (helpers/resolve-sql
          feed/posts
          {:feed/id 1}
          (fn [statement]
            (is (= [:post/id] (:select statement)))
            [{:post/id 2}])))))
