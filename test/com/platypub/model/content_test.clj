(ns com.platypub.model.content-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.graph :as graph]
            [com.platypub.model.content :as content]
            [com.platypub.test-helpers :as helpers]))

(deftest content-resolvers-test
  (let [stored (.getBytes "{\"html\":\"<p>Stored</p>\",\"text\":\"Stored\"}")]
    (is (= (seq stored)
           (seq (:content/blob
                 (helpers/resolve-resolver
                  content/blob
                  {:biff.fx/handlers
                   {:platypub.fx/get-object (fn [_ object-key]
                                              (is (= 1 object-key))
                                              {:headers {} :body stored})}}
                  {:content/id 1})))))
    (is (= {:content/string "{\"html\":\"<p>Stored</p>\",\"text\":\"Stored\"}"
            :content/json   {:html "<p>Stored</p>" :text "Stored"}
            :content/html   "<p>Stored</p>"
            :content/text   "Stored"}
           (helpers/resolve-resolver content/values {}
                                     {:content/blob stored}))))
  (is (= {:post/content {:content/id 2}}
         (helpers/resolve-resolver content/post-content {}
                                   {:post/content-id 2})))
  (is (= {:send/content {:content/id 3}}
         (helpers/resolve-resolver content/send-content {}
                                   {:send/content-id 3}))))

(deftest content-query-reads-object-body
  (let [ctx (merge
             {:biff.fx/handlers
              {:platypub.fx/get-object
               (fn [_ _]
                 {:headers {"content-type" "application/json"}
                  :body    (.getBytes "{\"html\":\"Stored\"}")})}}
             (graph/new-ctx [content/blob content/values]))]
    (is (= {:content/html "Stored"}
           (graph/query ctx {:content/id 1} [:content/html])))))
