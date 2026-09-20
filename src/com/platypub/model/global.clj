(ns com.platypub.model.global
  (:require [com.biffweb.graph :refer [defresolver]]
            [tick.core :as tick]))

(defresolver users
  {:output [{:global/users [:user/id]}]}
  (fn [_ctx _input]
    {:global/users
     [:biff.sqlite.fx/execute
      {:select   [:user/id]
       :from     :user
       :order-by [[:user/joined-at :asc]]}]}))

(defresolver active-feeds
  {:output [{:global/active-feeds [:feed/id]}]}
  (fn [_ctx _input]
    {:global/active-feeds
     [:biff.sqlite.fx/execute
      {:select-distinct [:feed/id]
       :from            :feed
       :join            [:publication [:= :publication/feed-id :feed/id]]}]}))

(defresolver publications
  {:output [{:global/publications [:publication/id]}]}
  (fn [_ctx _input]
    {:global/publications
     [:biff.sqlite.fx/execute
      {:select [:publication/id]
       :from   :publication}]}))

(defresolver user-count
  {:output [:global/user-count]}
  (fn [_ctx _input]
    [:biff.sqlite.fx/execute
     {:select [[[:count :*] :user/count]]
      :from   :user}])

  (fn [_ctx rows]
    {:global/user-count (or (:user/count (first rows)) 0)}))

(defresolver first-user
  {:output [{:global/first-user [:user/id]}]}
  (fn [_ctx _input]
    [:biff.sqlite.fx/execute
     {:select   [:user/id]
      :from     :user
      :order-by [[:user/joined-at :asc]]
      :limit    1}])

  (fn [_ctx rows]
    {:global/first-user (first rows)}))

(defresolver stale-sends
  {:output [{:global/stale-sends [:send/id]}]}
  (fn [{:biff.fx/keys [now]} _input]
    {:global/stale-sends
     [:biff.sqlite.fx/execute
      {:select [:send/id]
       :from   :send
       :where  [:and
                [:= :send/status [:lift :send.status/pending]]
                [:< :send/progress-at
                 (tick/<< now (tick/of-minutes 15))]]}]}))

(def module
  {:biff.graph/resolvers
   [users active-feeds publications user-count first-user stale-sends]})
