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

(defn- styled-single-html
  [publication post]
  (let [card?    (not= :publication.email-style/letter
                       (:publication/email-style publication))
        site-url (:publication/site-url publication)
        post-url (:post/url post)
        banner   (:publication/banner-image-url publication)
        accent   (:publication/primary-color publication)
        content  (or (get-in post [:post/content :content/html])
                     (get-in post [:post/content :content/text])
                     "")
        width    (if card? "596px" "546px")
        footer   [:div
                  {:style (if card?
                            "font-size:13px;margin-top:56px"
                            "font-size:13px;margin-top:2px")}
                  (:publication/address publication) ". "
                  [:a {:href  email/unsubscribe-placeholder
                       :style (str "color:"
                                   (if card? "#0366d6" accent)
                                   ";text-decoration:underline")}
                   "Unsubscribe"] "."]
        css      (str "body{font-family:ui-sans-serif,system-ui,-apple-system,"
                      "BlinkMacSystemFont,Segoe UI,Roboto,Arial,sans-serif;"
                      "font-size:16px;line-height:1.4;margin:0;}"
                      ".post-content img{max-width:100%;height:auto;"
                      "display:block;"
                      "margin:24px auto;}"
                      (if card?
                        (str ".post-content p,.post-content ul,"
                             ".post-content ol{line-height:1.5;"
                             "margin:0 0 16px;}")
                        (str ".post-content p,.post-content ul,"
                             ".post-content ol{line-height:1.6;"
                             "margin:20px 0;}"))
                      ".post-content blockquote{border-left:2px solid " accent
                      ";font-style:italic;margin:32px 0;padding:0 24px;}"
                      ".post-content a{color:"
                      (if card? "#0366d6" accent)
                      ";text-decoration:none;}"
                      ".post-content h1,.post-content h2,.post-content h3{"
                      "line-height:1.25;color:#1c1917;}"
                      (when card?
                        (str ".post-content h2{border-bottom:1px solid #e1e4e8;"
                             "padding-bottom:8px;}"))
                      (when-not card?
                        (str ".post-content{font-size:17px;color:#44403c;}"
                             ".post-content h2{margin-top:48px;"
                             "margin-bottom:24px;}")))]
    (chassis/html
     [chassis/doctype-html5
      [:html
       [:head
        [:meta {:charset "utf-8"}]
        [:meta {:name    "viewport"
                :content "width=device-width,initial-scale=1"}]
        [:title (:post/title post)]
        [:style css]]
       [:body
        {:style (str "padding-top:10px;background:"
                     (if card?
                       (:publication/padding-color publication)
                       "#ffffff")
                     ";color:" (:publication/text-color publication))}
        [:div {:style (str "max-width:" width ";margin:0 auto")}
         (when banner
           [:a {:href  site-url
                :style "display:block;text-decoration:none"}
            (if card?
              [:div {:style "padding:16px;text-align:center"}
               [:img {:src   banner
                      :alt   (:publication/title publication)
                      :style (str "max-height:30px;max-width:100%;"
                                  "display:block;margin:0 auto")}]]
              [:div {:style (str "height:75px;background-image:url('"
                                 banner "');background-repeat:no-repeat;"
                                 "background-position:center")}])])
         (when card? [:div {:style "height:12px"}])
         [:div
          {:style (if card?
                    "padding:16px;background:#ffffff"
                    "padding:26px 0 16px")}
          [:div.post-content
           (when-let [intro (:publication/intro publication)]
             [:div {:style "font-style:italic;margin-bottom:16px"}
              (chassis/raw intro)])
           (if card?
             [:h2 {:style "font-size:24px;margin:0 0 10px"}
              (:post/title post)]
             [:a {:href  post-url
                  :style (str "color:" accent
                              ";text-decoration:none")}
              [:h1 {:style "font-size:30px;margin:12px 0 26px"}
               (:post/title post)]])
           (when (and card? post-url)
             [:p [:a {:href  post-url
                      :style "text-decoration:none"} "Read online"]])
           [:div (chassis/raw content)]]
          (when-not card?
            [:hr {:style (str "margin:38px 0 14px;border:0;"
                              "border-top:1px solid #e7e5e4")}])
          footer]
         [:div {:style (if card? "height:32px" "height:12px")}]]]]])))

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
                                :publication/address
                                [:? :publication/intro]
                                [:? :publication/email-style]
                                [:? :publication/site-url]
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
             (when (seq image) [:img {:src image, :alt ""}])
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
            [:article {:style "margin-top:28px"}
             (when (and multiple (:post/title post))
               [:h2
                (if url
                  [:a {:href url :style link-style} (:post/title post)]
                  (:post/title post))])
             (when (not same-author)
               (author-view (author publication post)))
             (when (and (not multiple) url)
               [:p
                [:a {:href url :style link-style} "Read online"]])
             [:div
              body
              (when (and multiple url)
                ["… " [:a {:href url :style link-style} "Read more"]])]
             (when published
               [:p {:style "font-size:14px;opacity:.75"} published])]))

        html
        (if (and (not multiple)
                 (or (nil? (:publication/email-style publication))
                     (#{:publication.email-style/card
                        :publication.email-style/letter}
                      (:publication/email-style publication))))
          (styled-single-html publication (first posts))
          (chassis/html
           [chassis/doctype-html5
            [:html
             [:head
              [:meta {:charset "utf-8"}]
              [:meta {:name    "viewport"
                      :content "width=device-width,initial-scale=1"}]
              [:style
               (str "body{font-family:ui-sans-serif,system-ui,"
                    "-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,"
                    "Arial,sans-serif;font-size:16px;line-height:1.4;"
                    "margin:0}article p{line-height:1.5}")]]
             [:body
              {:style (str "padding-top:10px;background:"
                           (:publication/padding-color publication)
                           ";color:"
                           (:publication/text-color publication))}
              [:div {:style "max-width:596px;margin:0 auto"}
               (when-let [banner (:publication/banner-image-url publication)]
                 [:a {:href  (:publication/site-url publication)
                      :style "display:block;text-decoration:none"}
                  [:div {:style "padding:16px;text-align:center"}
                   [:img {:src   banner
                          :alt   (:publication/title publication)
                          :style (str "max-height:30px;max-width:100%;"
                                      "display:block;margin:0 auto")}]]])
               [:div {:style "height:12px"}]
               [:main
                {:style "padding:16px;background:#ffffff"}
                [:h1 {:style "font-size:24px;margin:0 0 10px"}
                 (:publication/title publication)]
                (when-let [intro (:publication/intro publication)]
                  [:div {:style "font-style:italic;margin-bottom:16px"}
                   (chassis/raw intro)])
                (author-view same-author)
                (map post-view posts)
                [:footer
                 {:style "font-size:13px;margin-top:56px"}
                 (:publication/address publication) ". "
                 [:a {:href  email/unsubscribe-placeholder
                      :style "color:#0366d6;text-decoration:underline"}
                  "Unsubscribe"] "."]]
               [:div {:style "height:32px"}]]]]]))]
    {:send/subject (subject posts)
     :send/html    html
     :send/text    (str (text/html->text html)
                        "\n\nUnsubscribe: " email/unsubscribe-placeholder)}))

(def module
  {:biff.graph/resolvers [posts delivery-subscribers rendered-content]})
