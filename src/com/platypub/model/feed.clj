(ns com.platypub.model.feed
  (:require [com.biffweb.graph :refer [defresolver]]))

(defresolver by-url
  {:input  [:feed/url]
   :output [:feed/id]}
  (fn [_ctx {:feed/keys [url]}]
    [:biff.sqlite.fx/execute
     {:select [:feed/id], :from :feed, :where [:= :feed/url url]}])

  (fn [_ctx rows]
    (first rows)))

(defresolver publications
  {:input  [:feed/id]
   :output [{:feed/publications [:publication/id]}]}
  (fn [_ctx {:feed/keys [id]}]
    {:feed/publications
     [:biff.sqlite.fx/execute
      {:select [:publication/id]
       :from   :publication
       :where  [:and
                [:= :publication/feed-id id]
                [:is :publication/archived-at nil]]}]}))

(defresolver posts
  {:input  [:feed/id]
   :output [{:feed/posts [:post/id]}]}
  (fn [_ctx {:feed/keys [id]}]
    {:feed/posts
     [:biff.sqlite.fx/execute
      {:select [:post/id]
       :from   :post
       :where  [:= :post/feed-id id]}]}))

(def module
  {:biff.graph/resolvers [by-url publications posts]})
