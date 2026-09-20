(ns com.platypub.model.publication
  (:require [clojure.string :as str]
            [com.biffweb.graph :refer [defresolver]]
            [tick.core :as tick]))

(defn- visible-posts-query
  [publication-id select]
  {:select-distinct select
   :from            :post
   :join            [[:publication :publication]
                     [:= :publication/feed-id :post/feed-id]]

   :left-join
   [[:send-post :send-post]
    [:= :send-post/post-id :post/id]
    [:send :send]
    [:and
     [:= :send/id :send-post/send-id]
     [:= :send/publication-id :publication/id]]]

   :where
   [:and
    [:= :publication/id publication-id]
    [:or
     [:is-not :send/id nil]
     [:>= :post/present-as-of :publication/feed-id-updated-at]]]

   :order-by
   [[:post/fetched-at :desc]
    [:post/published-at :desc]
    [:post/id :desc]]})

(defn- normalize-tag
  [tag]
  (some-> tag str/trim str/lower-case not-empty))

(defn- tags-match?
  [publication post]
  (let [tags       (set (keep normalize-tag (:post/tags post)))
        filter-tag (normalize-tag (:publication/filter-tag publication))
        remove-tag (normalize-tag (:publication/remove-tag publication))]
    (and (or (nil? filter-tag) (contains? tags filter-tag))
         (or (nil? remove-tag) (not (contains? tags remove-tag))))))

(defresolver sends
  {:input  [:publication/id]
   :output [{:publication/sends [:send/id]}]}
  (fn [_ctx {:publication/keys [id]}]
    {:publication/sends
     [:biff.sqlite.fx/execute
      {:select   [:send/id]
       :from     :send
       :where    [:= :send/publication-id id]
       :order-by [[:send/started-at :desc]]}]}))

(defresolver active-subscriber-count
  {:input  [:publication/id]
   :output [:publication/active-subscriber-count]}
  (fn [_ctx {:publication/keys [id]}]
    [:biff.sqlite.fx/execute
     {:select [[[:count :*] :subscriber/count]]
      :from   :subscriber
      :where  [:and
               [:= :subscriber/publication-id id]
               [:not= :subscriber/suppressed true]
               [:is :subscriber/unsubscribed-at nil]
               [:or
                [:= :subscriber/require-confirmation false]
                [:is-not :subscriber/confirmed-at nil]]]}])

  (fn [_ctx rows]
    {:publication/active-subscriber-count
     (or (:subscriber/count (first rows)) 0)}))

(defresolver subscribers
  {:input  [:publication/id]
   :output [{:publication/subscribers [:subscriber/id]}]}
  (fn [_ctx {:publication/keys [id]}]
    {:publication/subscribers
     [:biff.sqlite.fx/execute
      {:select [:subscriber/id]
       :from   :subscriber
       :where  [:= :subscriber/publication-id id]}]}))

(defresolver visible-posts
  {:input  [:publication/id]
   :output [{:publication/visible-posts [:post/id]}]}
  (fn [_ctx {:publication/keys [id]}]
    {:publication/visible-posts
     [:biff.sqlite.fx/execute (visible-posts-query id [:post/id])]}))

(defresolver automatic-posts
  {:input  [:publication/id
            :publication/automatic-send-threshold
            [:? :publication/latest-send-started-at]
            [:? :publication/filter-tag]
            [:? :publication/remove-tag]]
   :output [{:publication/automatic-posts [:post/id]}]}
  (fn [_ctx {:publication/keys [id] :as input}]
    {:input input
     :posts [:biff.sqlite.fx/execute
             (visible-posts-query
              id
              [:post/id :post/fetched-at :post/tags])]})

  (fn [_ctx {:keys [input posts]}]
    (let [threshold (:publication/automatic-send-threshold input)
          latest    (:publication/latest-send-started-at input)
          after     (if (or (nil? latest) (tick/> threshold latest))
                      threshold
                      latest)]
      {:publication/automatic-posts
       (->> posts
            (filter #(tick/> (:post/fetched-at %) after))
            (filter #(tags-match? input %))
            vec)})))

(def module
  {:biff.graph/resolvers
   [sends
    active-subscriber-count
    subscribers
    visible-posts
    automatic-posts]})
