(ns com.platypub.model.send-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.platypub.model.send :as send]
            [com.platypub.test-helpers :as helpers]
            [tick.core :as tick]))

(deftest posts-resolver-test
  (is (= {:send/posts [{:post/id 1}]}
         (helpers/resolve-sql send/posts
                              {:send/id 2}
                              [{:post/id 1}]))))

(deftest rendered-content-resolver-test
  (let [now (tick/instant "2026-01-01T00:00:00Z")

        result
        (helpers/resolve-resolver
         send/rendered-content
         {}
         {:send/publication
          {:publication/title            "News"
           :publication/padding-color    "#fff"
           :publication/background-color "#fff"
           :publication/text-color       "#111"}

          :send/posts
          [{:post/id         1
            :post/title      "Hello"
            :post/fetched-at now
            :post/content-id 2
            :content/data    {:html "<p>Body</p>" :text "Body"}}]})]
    (is (= "Hello" (:send/subject result)))
    (is (str/includes? (:send/html result) "<p>Body</p>"))
    (is (str/includes? (:send/text result) "Unsubscribe:"))))
