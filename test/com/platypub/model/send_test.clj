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
           :publication/address          "123 Main St"
           :publication/intro            "<strong>Intro</strong>"
           :publication/padding-color    "#fff"
           :publication/background-color "#fff"
           :publication/text-color       "#111"}

          :send/posts
          [{:post/id         1
            :post/title      "Hello"
            :post/fetched-at now
            :post/content-id 2
            :post/content    {:content/html "<p>Body</p>"
                              :content/text "Body"}}]})]
    (is (= "Hello" (:send/subject result)))
    (is (str/includes? (:send/html result) "<p>Body</p>"))
    (is (str/includes? (:send/html result) "123 Main St"))
    (is (str/includes? (:send/html result) "<strong>Intro</strong>"))
    (is (str/includes? (:send/text result) "Unsubscribe:"))))

(deftest styled-single-email-test
  (let [post {:post/id 1
              :post/title "Hello"
              :post/url "https://example.com/p/hello"
              :post/fetched-at (tick/instant "2026-01-01T00:00:00Z")
              :post/content {:content/html "<p>Body</p>"}}
        publication {:publication/title "Newsletter"
                     :publication/address "123 Main St"
                     :publication/site-url "https://example.com"
                     :publication/banner-image-url "https://example.com/banner.png"
                     :publication/padding-color "#eee"
                     :publication/background-color "#fff"
                     :publication/text-color "#111"
                     :publication/primary-color "#db2777"}
        render (fn [style]
                 (helpers/resolve-resolver
                  send/rendered-content
                  {}
                  {:send/publication
                   (assoc publication :publication/email-style style)
                   :send/posts [post]}))
        card (:send/html (render :publication.email-style/card))
        letter (:send/html
                (render :publication.email-style/letter))]
    (is (str/includes? card "max-width:596px"))
    (is (str/includes? card "Read online"))
    (is (str/includes? card "background:#fff"))
    (is (str/includes? letter "max-width:546px"))
    (is (str/includes? letter "height:75px"))
    (is (str/includes? letter "https://example.com/p/hello"))
    (is (str/includes? letter "Unsubscribe"))))

(deftest multi-posts-use-card-layout-test
  (let [publication {:publication/title "Newsletter"
                     :publication/email-style :publication.email-style/letter
                     :publication/address "123 Main St"
                     :publication/padding-color "#eee"
                     :publication/background-color "#fff"
                     :publication/text-color "#111"
                     :publication/primary-color "#db2777"}
        now (tick/instant "2026-01-01T00:00:00Z")
        posts (mapv (fn [n]
                      {:post/id n
                       :post/title (str "Post " n)
                       :post/url (str "https://example.com/" n)
                       :post/fetched-at now
                       :post/excerpt "Excerpt"
                       :post/content {:content/html "<p>Body</p>"}})
                    [1 2])
        html (:send/html
              (helpers/resolve-resolver send/rendered-content {}
                                        {:send/publication publication
                                         :send/posts posts}))]
    (is (str/includes? html "max-width:596px"))
    (is (str/includes? html "padding:16px;background:#fff"))
    (is (= 2 (count (re-seq #"<article" html))))))
