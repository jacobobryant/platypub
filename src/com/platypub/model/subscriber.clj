(ns com.platypub.model.subscriber
  (:require [com.biffweb.graph :refer [defresolver]]))

(defresolver by-publication-email
  {:input  [:subscriber/publication-id :subscriber/email]
   :output [:subscriber/id]}
  (fn [_ctx {:subscriber/keys [publication-id email]}]
    [:biff.sqlite.fx/execute
     {:select [:subscriber/id]
      :from   :subscriber
      :where  [:and
               [:= :subscriber/publication-id publication-id]
               [:= :subscriber/email email]]}])

  (fn [_ctx rows]
    (first rows)))

(defresolver by-confirmation-token
  {:input  [:subscriber/confirmation-token]
   :output [:subscriber/id]}
  (fn [_ctx {:subscriber/keys [confirmation-token]}]
    [:biff.sqlite.fx/execute
     {:select [:subscriber/id]
      :from   :subscriber
      :where  [:= :subscriber/confirmation-token confirmation-token]}])

  (fn [_ctx rows]
    (first rows)))

(defresolver active
  {:input  [:subscriber/id
            [:? :subscriber/suppressed]
            [:? :subscriber/unsubscribed-at]
            :subscriber/require-confirmation
            [:? :subscriber/confirmed-at]]
   :output [:subscriber/active]}
  [_ctx subscriber]
  {:subscriber/active
   (and (some? (:subscriber/id subscriber))
        (not (:subscriber/suppressed subscriber))
        (nil? (:subscriber/unsubscribed-at subscriber))
        (or (not (:subscriber/require-confirmation subscriber))
            (some? (:subscriber/confirmed-at subscriber))))})

(def module
  {:biff.graph/resolvers [by-publication-email by-confirmation-token active]})
