(ns com.platypub.app.publications.publication.send
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.middleware :as mid]
            [com.platypub.lib.tab :as tab]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]))

(defpath root-path "")
(defpath confirm-path "/app/publications/:publication-id/send/confirm")

(defpipeline send-page
  [:biff.graph.fx/query
   [{[:? :request/publication]
     [:publication/id
      :publication/title
      {:publication/sends [{:send/posts [:post/id]}]}
      {:publication/visible-posts [:post/id [:? :post/title]]}]}
    {[:? :request/send-preview]
     [[:? :send/revision] :send/subject :send/html
      :send/from-name :send/reply-to
      {:send/posts [:post/id]}]}]]

  (fn [request result]
    (if-let [publication (:request/publication result)]
      (let [preview (:request/send-preview result)

            used
            (set (map :post/id
                      (mapcat :send/posts
                              (:publication/sends publication))))

            posts
            (remove #(used (:post/id %))
                    (:publication/visible-posts publication))]
        (ui/app-shell
         request
         [:main
          {:data-signals
           (datastar/signals-json
            {:send/dialogopen (boolean preview)
             :send/revision   (some-> preview :send/revision str)})

           :class ["mx-auto max-w-3xl p-6"]}
          [:a
           {:href  (routes/publication (:publication/id
                                        publication)),
            :class ["text-primary"]} "← Publication"]
          [:h1
           {:class ["my-4 text-3xl font-bold"]}
           "Send newsletter"]
          [:form
           {:data-on:submit "@post(el.dataset.action)"
            :data-action    (routes/publication-send
                             (:publication/id publication))
            :data-indicator "send_previewing"

            :data-signals__ifmissing
            (datastar/signals-json {:send/post-ids []})}
           (for [post posts]
             [:label {:class ["mb-2 flex gap-3 rounded border border-border"
                              "bg-surface p-4"]}
              [:input {:type      "checkbox"
                       :data-bind (datastar/signal-name :send/post-ids)
                       :value     (:post/id post)}]
              (or (:post/title post) "Untitled post")])
           [:button
            {:class              ["mt-4 rounded bg-primary px-4 py-2 text-white"
                                  "disabled:opacity-60"]
             :data-attr:disabled "$send_previewing"}
            [:span {:data-show "!$send_previewing"} "Preview"]
            [:span {:data-show "$send_previewing"
                    :style     "display: none"}
             "Preparing preview…"]]]
          (when preview
            (ui/modal
             {:class ["w-full max-w-3xl rounded border border-border p-0"
                      "bg-surface shadow-xl"]}
             "$send_dialogopen"
             "$send_dialogopen = false"
             [:h2 {:class ["border-b border-border p-5 text-xl font-semibold"]}
              (:send/subject preview)]
             [:iframe {:title             "Newsletter preview"
                       :data-preview-html (:send/html preview)

                       :data-effect
                       (str "$send_revision; "
                            "el.srcdoc = DOMPurify.sanitize("
                            "el.dataset.previewHtml, {WHOLE_DOCUMENT: true})")

                       :sandbox "allow-same-origin"
                       :style   {:height "min(720px, calc(100svh - 12rem))"}
                       :class   ["block w-full border-0"]}]
             [:div
              {:class ["flex justify-end gap-2 border-t border-border p-5"]}
              [:button {:type "button"

                        :data-on:click
                        "$send_dialogopen = false"

                        :class
                        ["rounded border border-border px-4 py-2"]}
               "Cancel"]
              [:form
               {:data-on:submit
                (str "@post(el.dataset.action).then(() => "
                     "window.location.href='"
                     (routes/publication (:publication/id publication))
                     "')")

                :data-action             (confirm-path
                                          (:publication/id publication))
                :data-signals__ifmissing (datastar/signals-json {})}
               [:button {:class ["rounded bg-primary px-4 py-2 text-white"]}
                "Send"]]]))]))
      {:status 404})))

(defpipeline preview-send
  [:biff.graph.fx/query
   [{[:? :request/publication]
     [:publication/id
      :publication/title
      [:? :publication/reply-to]
      [:? :publication/address]
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
      :publication/primary-color
      :publication/from-name
      :publication/reply-to-address]}
    {:request/send-selection [[:? :send/post-ids]]}
    {:request/send-posts
     [:post/id
      [:? :post/url]
      :post/fetched-at
      [:? :post/published-at]
      [:? :post/title]
      [:? :post/author-name]
      [:? :post/author-url]
      [:? :post/author-image-url]
      [:? :post/excerpt]
      :post/content-id
      {:post/content [[:? :content/html] [:? :content/text]]}]}
    {:request/tab
     [{[:? :tab/send-preview]
       [:publication/id
        :send/subject
        :send/html
        :send/text
        :send/from-name
        :send/reply-to
        :send/post-ids]}]}]]

  (fn [{:keys [biff.datastar/tab-id]} result]
    (if (and (:request/publication result)
             (not (str/blank?
                   (get-in result [:request/publication
                                   :publication/address])))
             (seq (:request/send-posts result)))
      {:tab-id tab-id
       :result result

       :content
       [:biff.graph.fx/query
        {:send/publication (:request/publication result)
         :send/posts       (:request/send-posts result)}
        [:send/subject :send/html :send/text]]}
      {:status 404}))

  (fn [{:biff.fx/keys [random-uuid7-seq]}
       {:keys [tab-id result content]}]
    (let [publication (:request/publication result)

          posts (:request/send-posts result)]
      {:_preview
       [:biff.sqlite.fx/execute
        (tab/write-statement
         tab-id
         (:request/tab result)
         {:tab/send-preview
          {:publication/id (:publication/id publication)
           :send/revision  (first random-uuid7-seq)
           :send/subject   (:send/subject content)
           :send/html      (:send/html content)
           :send/text      (:send/text content)
           :send/from-name (:publication/from-name publication)
           :send/reply-to  (:publication/reply-to-address publication)
           :send/post-ids  (mapv :post/id posts)}})]

       :biff.fx/return {:status 204}})))

(defpipeline confirm-send
  [:biff.graph.fx/query
   [{[:? :request/publication]
     [:publication/id
      :publication/title
      [:? :publication/reply-to]
      [:? :publication/address]
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
      :publication/primary-color
      :publication/from-name
      :publication/reply-to-address]}
    {:request/send-posts
     [:post/id
      [:? :post/url]
      :post/fetched-at
      [:? :post/published-at]
      [:? :post/title]
      [:? :post/author-name]
      [:? :post/author-url]
      [:? :post/author-image-url]
      [:? :post/excerpt]
      :post/content-id
      {:post/content [[:? :content/html] [:? :content/text]]}]}
    {[:? :request/send-preview]
     [:send/subject :send/html :send/text :send/from-name :send/reply-to
      {:send/posts [:post/id]}]}
    {:request/tab
     [{[:? :tab/send-preview]
       [:publication/id
        :send/subject
        :send/html
        :send/text
        :send/from-name
        :send/reply-to
        :send/post-ids]}]}]]

  (fn [{:biff.fx/keys       [now random-uuid7-seq]
        :biff.datastar/keys [tab-id]}
       result]
    (let [publication (:request/publication result)

          posts (:request/send-posts result)]
      (if (and publication
               (not (str/blank? (:publication/address publication)))
               (seq posts))
        {:now    now
         :ids    random-uuid7-seq
         :tab-id tab-id
         :result result

         :content
         [:biff.graph.fx/query
          {:send/publication publication
           :send/posts       posts}
          [:send/subject :send/html :send/text]]}
        {:biff.fx/return {:status 204}})))

  (fn [_ctx {:keys [now ids tab-id result content]}]
    (let [publication (:request/publication result)

          posts (:request/send-posts result)

          send-id (first ids)

          content-id (second ids)

          row-ids (drop 2 ids)

          stored-content {:html (:send/html content)
                          :text (:send/text content)}]
      {:send-id    send-id
       :content-id content-id
       :content    stored-content
       :write-data {:now         now
                    :tab-id      tab-id
                    :result      result
                    :publication publication
                    :posts       posts
                    :row-ids     row-ids
                    :send-id     send-id
                    :content-id  content-id
                    :content     stored-content
                    :rendered    content}

       :_content [:platypub.fx/put-object
                  {:key          content-id
                   :value        (json/generate-string stored-content)
                   :content-type "application/json"}]}))

  (fn [_ctx {:keys [write-data]}]
    (let [{:keys [now tab-id result publication posts row-ids send-id
                  content-id]}
          write-data

          rendered (:rendered write-data)]
      {:send-id send-id

       :clear-preview
       (tab/write-statement tab-id (:request/tab result)
                            {:tab/send-preview nil})

       :_write
       [:biff.sqlite.fx/authorized-write-tx
        (into
         [{:insert-into :send
           :values      [{:send/id             send-id
                          :send/publication-id (:publication/id publication)
                          :send/started-at     now
                          :send/progress-at    now
                          :send/status         [:lift :send.status/pending]

                          :send/from-name
                          (:publication/from-name publication)

                          :send/reply-to
                          (:publication/reply-to-address publication)

                          :send/subject    (:send/subject rendered)
                          :send/content-id content-id

                          :send/provenance [:lift :send.provenance/manual]}]}]
         (map (fn [post row-id]
                {:insert-into :send-post
                 :values      [{:send-post/id      row-id
                                :send-post/send-id send-id
                                :send-post/post-id (:post/id post)}]})
              posts
              row-ids))]}))

  (fn [_ctx {:keys [send-id clear-preview]}]
    {:send-id        send-id
     :_clear-preview [:biff.sqlite.fx/execute clear-preview]})

  (fn [_ctx {:keys [send-id]}]
    {:_submit
     [:biff.background.fx/submit-jobs
      :platypub/send
      [{:send-id                  send-id
        :biff.background/priority 0}]]

     :biff.fx/return {:status 204}}))

(def module
  {:biff.ring/routes
   [[(root-path)
     {:middleware [mid/wrap-app-access]}
     [(routes/publication-send)
      {:get send-page, :post preview-send}]
     [(confirm-path) {:post confirm-send}]]]})
