(ns com.platypub.work.readiness-consumer-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.platypub.work.readiness-consumer :as readiness]
            [tick.core :as tick])
  (:import [java.util UUID]))

(def now (tick/instant "2026-09-13T00:00:00Z"))
(def publication
  {:publication/id                       1
   :publication/title                    "Publication"
   :publication/user                     {:user/email "owner@example.com"}
   :publication/automatic-send-threshold now})
(def post
  {:post/id      (UUID/fromString "00000000-0000-0000-0000-000000000004")
   :post/content {:content/html "<p>Hello</p>"
                  :content/text "Hello"}})

(deftest readiness-consumer-states
  (let [[load-publication select-posts render-content create-send]
        (readiness/readiness-consumer)

        id-strings
        ["00000000-0000-0000-0000-000000000001"
         "00000000-0000-0000-0000-000000000002"
         "00000000-0000-0000-0000-000000000003"
         "00000000-0000-0000-0000-000000000005"]

        ids (mapv #(UUID/fromString %) id-strings)]
    (is (= {:publication
            [:biff.graph.fx/query
             {:publication/id 1}
             [:publication/id
              :publication/title
              [:? :publication/intro]
              [:? :publication/banner-image-url]
              [:? :publication/default-author-name]
              [:? :publication/default-author-url]
              [:? :publication/default-author-image-url]
              :publication/padding-color
              :publication/background-color
              :publication/text-color
              :publication/primary-color
              [:? :publication/filter-tag]
              [:? :publication/remove-tag]
              [:? :publication/automatic-send-threshold]
              :publication/active-subscriber-count
              {:publication/user [:user/email]}
              {:publication/sends [:send/id :send/status :send/started-at]}]]}
           (load-publication
            {:biff.background/job {:publication/id 1}})))
    (testing "ineligible publications exit without a post query"
      (is (= {:biff.fx/return nil}
             (select-posts {:biff.fx/now now}
                           {:publication {}})))
      (is (= {:biff.fx/return nil}
             (select-posts {:biff.fx/now now}
                           {:publication
                            (assoc publication
                                   :publication/active-subscriber-count 1
                                   :publication/sends
                                   [{:send/status     :send.status/pending
                                     :send/started-at now}])})))
      (is (= {:biff.fx/return nil}
             (select-posts {:biff.fx/now now}
                           {:publication
                            (assoc publication
                                   :publication/active-subscriber-count 1
                                   :publication/sends
                                   [{:send/started-at
                                     (tick/<< now (tick/of-hours 23))}])}))))
    (testing "eligible publications load automatic posts"
      (let [result (select-posts {:biff.fx/now now}
                                 {:publication
                                  (assoc publication
                                         :publication/active-subscriber-count 1
                                         :publication/sends
                                         [{:send/started-at
                                           (tick/<< now
                                                    (tick/of-hours 24))}])})]
        (is (= (assoc publication
                      :publication/active-subscriber-count 1
                      :publication/sends
                      [{:send/started-at (tick/<< now (tick/of-hours 24))}])
               (:publication result)))
        (is (= :biff.graph.fx/query (first (:posts result))))))
    (testing "empty post selections exit"
      (is (= {:biff.fx/return nil}
             (render-content
              {}
              {:publication publication
               :posts       {:publication/automatic-posts []}}))))
    (testing "selected posts create a send and enqueue it"
      (let [rendered
            (render-content
             {}
             {:publication publication
              :posts       {:publication/automatic-posts [post]}})

            effects
            (:biff.fx/seq
             (create-send
              {:biff.fx/now              now
               :biff.fx/random-uuid7-seq ids}
              (assoc rendered
                     :content
                     {:send/subject "Subject"
                      :send/html    "<p>Hello</p>"
                      :send/text    "Hello"})))]
        (is (= :biff.graph.fx/query (get-in rendered [:content 0])))
        (is (= :platypub.fx/put-object
               (get-in effects [0 :_content 0])))
        (is (= "owner@example.com"
               (get-in effects [1 :_write 1 0 :values 0 :send/reply-to])))
        (is (= :biff.sqlite.fx/execute-tx
               (get-in effects [1 :_write 0])))
        (is (= [:biff.background.fx/submit-jobs
                :platypub/send
                [{:send-id (first ids)}]]
               (get-in effects [2 :_submit])))))))
