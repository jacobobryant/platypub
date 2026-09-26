(ns com.platypub.model.send
  (:require [com.biffweb.graph :refer [defresolver]]
            [com.platypub.lib.email :as email]
            [com.platypub.lib.text :as text]
            [dev.onionpancakes.chassis.core :as chassis]
            [tick.core :as tick]))

(def ^:private max-instant
  (tick/instant "9999-12-31T23:59:59.999999999Z"))

(defn- post-sort-key
  [{:post/keys [url fetched-at published-at id]}]
  [(if url 0 1) fetched-at (or published-at max-instant) (str id)])

(defn- author
  [publication post]
  (if-let [author-name (:post/author-name post)]
    {:name  author-name
     :url   (:post/author-url post)
     :image (:post/author-image-url post)}
    (when-let [author-name (:publication/default-author-name publication)]
      {:name  author-name
       :url   (:publication/default-author-url publication)
       :image (:publication/default-author-image-url publication)})))

(defn- subject
  [posts]
  (or (some :post/title posts)
      (let [post    (first posts)
            content (or (get-in post [:post/content :content/text]) "")]
        (str (subs content 0 (min 40 (count content)))
             (when (> (count content) 40) "…")))))

(defn- rfc-1123
  [instant]
  (tick/format (tick/formatter "EEE, dd MMM uuuu HH:mm:ss 'GMT'")
               (tick/in instant "UTC")))

(defresolver posts
  {:input  [:send/id]
   :output [{:send/posts [:post/id]}]}
  (fn [_ctx {:send/keys [id]}]
    {:send/posts
     [:biff.sqlite.fx/execute
      {:select [[:send-post/post-id :post/id]]
       :from   :send-post
       :where  [:= :send-post/send-id id]}]}))

(defresolver delivery-subscribers
  {:input  [:send/id :send/started-at]
   :output [{:send/delivery-subscribers [:subscriber/id :subscriber/email]}]}
  (fn [_ctx {:send/keys [id started-at]}]
    {:send/delivery-subscribers
     [:biff.sqlite.fx/execute
      {:select [:subscriber/id :subscriber/email]
       :from   :subscriber
       :join   [:send [:= :send/id id]]
       :where  [:and
                [:= :subscriber/publication-id :send/publication-id]
                [:< :subscriber/subscribed-at started-at]
                [:or
                 [:is :subscriber/suppressed nil]
                 [:= :subscriber/suppressed false]]
                [:is :subscriber/unsubscribed-at nil]
                [:or
                 [:= :subscriber/require-confirmation false]
                 [:is-not :subscriber/confirmed-at nil]]]}]}))

(defresolver rendered-content
  {:input  [{:send/publication [:publication/title
                                [:? :publication/intro]
                                [:? :publication/banner-image-url]
                                [:? :publication/default-author-name]
                                [:? :publication/default-author-url]
                                [:? :publication/default-author-image-url]
                                :publication/padding-color
                                :publication/background-color
                                :publication/text-color
                                :publication/primary-color]}
            {:send/posts [:post/id
                          [:? :post/url]
                          :post/fetched-at
                          [:? :post/published-at]
                          [:? :post/title]
                          [:? :post/author-name]
                          [:? :post/author-url]
                          [:? :post/author-image-url]
                          [:? :post/excerpt]
                          {:post/content
                           [[:? :content/html] [:? :content/text]]}]}]
   :output [:send/subject :send/html :send/text]}
  [_ctx input]
  (let [publication (:send/publication input)
        posts       (vec (sort-by post-sort-key (:send/posts input)))
        multiple    (> (count posts) 1)
        authors     (mapv #(author publication %) posts)
        same-author (when (and multiple (seq authors) (apply = authors))
                      (first authors))

        link-style (str "color:" (:publication/primary-color publication))

        author-view
        (fn [{author-name :name, :keys [url image]}]
          (when author-name
            [:div.author
             (when image [:img {:src image, :alt ""}])
             (if url
               [:a {:href url :style link-style} author-name]
               author-name)]))

        post-view
        (fn [post]
          (let [url       (:post/url post)
                content   (:post/content post)
                raw       (or (not multiple) (nil? url))
                body      (if raw
                            (chassis/raw
                             (or (:content/html content)
                                 (when-let [plain (:content/text content)]
                                   (chassis/html [:<> plain]))))
                            (:post/excerpt post))
                published (some-> (:post/published-at post) rfc-1123)]
            [:article
             (when-let [title (:post/title post)]
               [:h2 title])
             (when (not same-author)
               (author-view (author publication post)))
             (when published
               (if url
                 [:a {:href url :style link-style} published]
                 published))
             [:div body]
             (when (and url (or (not multiple) (nil? published)))
               [:p
                [:a {:href  url
                     :style (str link-style
                                 ";display:inline-block;padding:10px 14px;"
                                 "border:1px solid currentColor")}
                 "Read online"]])]))

        html
        (chassis/html
         [chassis/doctype-html5
          [:html
           [:body
            {:style (str "margin:0;background:"
                         (:publication/padding-color publication))}
            [:main
             {:style
              (str "max-width:680px;margin:auto;padding:32px;"
                   "background:"
                   (:publication/background-color publication)
                   ";color:"
                   (:publication/text-color publication))}
             (when-let [banner (:publication/banner-image-url publication)]
               [:img {:style "max-width:100%"
                      :src   banner
                      :alt   ""}])
             [:h1 (:publication/title publication)]
             (when-let [intro (:publication/intro publication)]
               [:p [:em intro]])
             (author-view same-author)
             (map post-view posts)
             [:footer
              [:a
               {:href email/unsubscribe-placeholder :style link-style}
               "Unsubscribe"]]]]]])]
    {:send/subject (subject posts)
     :send/html    html
     :send/text    (str (text/html->text html)
                        "\n\nUnsubscribe: " email/unsubscribe-placeholder)}))

(def module
  {:biff.graph/resolvers [posts delivery-subscribers rendered-content]})
