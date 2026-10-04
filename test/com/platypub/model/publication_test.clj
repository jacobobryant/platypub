(ns com.platypub.model.publication-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.graph :as graph]
            [com.platypub.model.publication :as publication]
            [com.platypub.test-helpers :as helpers]
            [tick.core :as tick]))

(deftest publication-sender-resolver
  (let [ctx   (graph/new-ctx [publication/publication-sender])
        input {:publication/title "News"
               :publication/user  {:user/email "owner@example.com"}}
        query [:publication/from-name :publication/reply-to-address]]
    (is (= {:publication/from-name        "News"
            :publication/reply-to-address "owner@example.com"}
           (graph/query ctx input query)))
    (is (= {:publication/from-name        "News"
            :publication/reply-to-address "replies@example.com"}
           (graph/query ctx
                        (assoc input :publication/reply-to
                               "replies@example.com")
                        query)))))

(deftest collection-resolvers-test
  (is (= {:publication/sends [{:send/id 1}]}
         (helpers/resolve-sql publication/sends
                              {:publication/id 2}
                              [{:send/id 1}])))
  (is (= {:publication/active-subscriber-count 3}
         (helpers/resolve-sql publication/active-subscriber-count
                              {:publication/id 2}
                              [{:subscriber/count 3}])))
  (is (= {:publication/subscribers [{:subscriber/id 1}]}
         (helpers/resolve-sql publication/subscribers
                              {:publication/id 2}
                              [{:subscriber/id 1}])))
  (is (= {:publication/visible-posts [{:post/id 1}]}
         (helpers/resolve-sql publication/visible-posts
                              {:publication/id 2}
                              [{:post/id 1}]))))

(deftest automatic-posts-resolver-test
  (let [threshold (tick/instant "2026-01-01T00:00:00Z")

        included
        {:post/id         1
         :post/fetched-at (tick/>> threshold (tick/of-hours 1))
         :post/tags       ["News"]}

        old
        {:post/id         2
         :post/fetched-at (tick/<< threshold (tick/of-hours 1))
         :post/tags       ["News"]}]
    (is (= {:publication/automatic-posts [included]}
           (helpers/resolve-sql
            publication/automatic-posts
            {:publication/id                       3
             :publication/automatic-send-threshold threshold
             :publication/filter-tag               "news"
             :publication/remove-tag               "draft"
             :publication/latest-send-started-at   nil}
            [included old])))))
