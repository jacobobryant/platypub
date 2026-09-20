(ns tasks.review-lint-test
  (:require [clojure.test :refer [deftest is testing]]
            [tasks.review-lint :as review-lint]))

(defn definition [namespace-name var-name]
  {:ns            namespace-name,
   :name          var-name,
   :filename      "src/example.clj",
   :row           1,
   :fixed-arities [0]})

(deftest lib-usage-violations-test
  (let [function (definition "com.example.lib.shared" "f")]
    (testing "warns below two distinct consumers"
      (is (= ["f"]
             (map :name
                  (review-lint/lib-usage-violations
                   {:var-definitions [function]
                    :var-usages      [{:to   "com.example.lib.shared",
                                       :name "f",
                                       :from "com.example.app.one"}]})))))
    (testing "does not count calls from the defining namespace"
      (is (= 1
             (count
              (review-lint/lib-usage-violations
               {:var-definitions [function]
                :var-usages      [{:to   "com.example.lib.shared",
                                   :name "f",
                                   :from "com.example.lib.shared"}
                                  {:to   "com.example.lib.shared",
                                   :name "f",
                                   :from "com.example.app.one"}]})))))
    (testing "counts namespaces instead of call sites"
      (is (empty?
           (review-lint/lib-usage-violations
            {:var-definitions [function]
             :var-usages      [{:to   "com.example.lib.shared",
                                :name "f",
                                :from "com.example.app.one"}
                               {:to   "com.example.lib.shared",
                                :name "f",
                                :from "com.example.app.two"}]}))))
    (testing "ignores private functions and public data"
      (is (empty?
           (review-lint/lib-usage-violations
            {:var-definitions [(assoc function :private true)
                               (dissoc function :fixed-arities)]
             :var-usages      []}))))))

(deftest module-require-violations-test
  (let [valid {:from "com.example.modules", :to "com.example.app.home"}

        invalid
        {:from     "com.example.lib.shared",
         :to       "com.example.app.home",
         :filename "src/example.clj",
         :row      2}]
    (is (empty? (review-lint/module-require-violations
                 {:namespace-usages [valid]})))
    (is (= #{:multiple-module-requirers :invalid-module-requirer}
           (set (map :kind
                     (review-lint/module-require-violations
                      {:namespace-usages [valid invalid]})))))))

(deftest different-modules-have-different-requirers-test
  (let [violations
        (review-lint/module-require-violations
         {:namespace-usages
          [{:from "com.example.stuff.a", :to "com.example.app.foo"}
           {:from "com.example.stuff.b", :to "com.example.app.bar"}]})]
    (is (= 1 (count (filter #(= :multiple-module-requirers (:kind %))
                            violations))))
    (is (= #{"com.example.stuff.a" "com.example.stuff.b"}
           (->> violations
                (filter #(= :invalid-module-requirer (:kind %)))
                (map :from)
                set)))))
