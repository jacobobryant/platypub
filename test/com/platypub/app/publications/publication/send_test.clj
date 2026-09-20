(ns com.platypub.app.publications.publication.send-test
  (:require [clojure.test :refer [deftest is]]
            [com.platypub.app.publications.publication.send :as send]
            [tick.core :as tick]))

(deftest send-page-state-test
  (let [[state]
        (send/send-page)

        publication-id
        (random-uuid)

        post-id
        (random-uuid)]
    (is (= {:status 404} (state {} {})))
    (is (= 200
           (:status
            (state {}
                   {:request/publication
                    {:publication/id            publication-id
                     :publication/sends         []
                     :publication/visible-posts [{:post/id    post-id
                                                  :post/title "Post"}]}}))))
    (is (= 200
           (:status
            (state {}
                   {:request/publication
                    {:publication/id            publication-id
                     :publication/sends         []
                     :publication/visible-posts []}

                    :request/send-preview
                    {:send/subject "Preview"
                     :send/html    "<p>Preview</p>"}}))))))

(deftest preview-send-state-test
  (let [[load-content write-preview]
        (send/preview-send)

        publication
        {:publication/id (random-uuid)}

        post
        {:post/id (random-uuid)}

        tab-id
        (random-uuid)]
    (is (= {:status 404} (load-content {} {})))
    (is (= {:status 404}
           (load-content {} {:request/publication publication
                             :request/send-posts  []})))
    (let [loaded (load-content
                  {:biff.datastar/tab-id tab-id}
                  {:request/publication publication
                   :request/send-posts  [post]
                   :request/tab         {}})]
      (is (= :biff.graph.fx/query (get-in loaded [:content 0])))
      (let [result (write-preview
                    {}
                    (assoc loaded :content {:send/subject "Subject"
                                            :send/html    "<p>Body</p>"}))]
        (is (= :biff.sqlite.fx/execute
               (get-in result [:_preview 0])))
        (is (= {:status 204} (:biff.fx/return result)))))))

(deftest confirm-send-states-test
  (let [[load-content create submit]
        (send/confirm-send)

        publication
        {:publication/id (random-uuid)}

        post
        {:post/id (random-uuid)}

        now
        (tick/instant "2026-01-01T00:00:00Z")

        ids
        (repeatedly 3 random-uuid)

        tab-id
        (random-uuid)]
    (is (= {:biff.fx/return {:status 204}}
           (load-content {}
                         {:request/publication publication
                          :request/send-posts  []})))
    (let [loaded
          (load-content
           {:biff.fx/now              now
            :biff.fx/random-uuid7-seq ids
            :biff.datastar/tab-id     tab-id}
           {:request/publication publication
            :request/send-posts  [post]
            :request/tab         {}})

          created
          (create
           {}
           (assoc loaded :content {:send/subject "Subject"
                                   :send/html    "<p>Body</p>"
                                   :send/text    "Body"}))]
      (is (= :biff.graph.fx/query (get-in loaded [:content 0])))
      (is (= (first ids) (:send-id created)))
      (is (= :biff.sqlite.fx/authorized-write-tx
             (get-in created [:_write 0]))))
    (let [send-id
          (random-uuid)

          result
          (submit {} {:send-id send-id})]
      (is (= :biff.background.fx/submit-jobs
             (get-in result [:_submit 0])))
      (is (= {:status 204} (:biff.fx/return result))))))
