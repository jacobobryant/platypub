(ns com.platypub.model.user
  (:require [com.biffweb.graph :refer [defresolver]]))

(defresolver by-email
  {:input [:user/email], :output [:user/id]}
  (fn [_ctx {:user/keys [email]}]
    [:biff.sqlite.fx/execute
     {:select [:user/id], :from :user, :where [:= :user/email email]}])

  (fn [_ctx rows]
    (first rows)))

(defresolver publications
  {:input [:user/id], :output [{:user/publications [:publication/id]}]}
  (fn [_ctx {:user/keys [id]}]
    {:user/publications
     [:biff.sqlite.fx/execute
      {:select   [:publication/id]
       :from     :publication
       :where    [:= :publication/user-id id]
       :order-by [[:publication/created-at :desc]]}]}))

(def module
  {:biff.graph/resolvers [by-email publications]})
