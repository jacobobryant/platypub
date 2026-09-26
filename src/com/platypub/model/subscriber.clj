(ns com.platypub.model.subscriber
  (:require [com.biffweb.graph :refer [defresolver]]
            [tick.core :as tick]))

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
   (and (not (:subscriber/suppressed subscriber))
        (nil? (:subscriber/unsubscribed-at subscriber))
        (or (not (:subscriber/require-confirmation subscriber))
            (some? (:subscriber/confirmed-at subscriber))))})

(defresolver confirmation-token-active
  {:input  [[:? :subscriber/confirmation-triggered-at]]
   :output [:subscriber/confirmation-token-active]}
  ;; make this defresolver use the biff.fx from (wrap the below code in a `(fn
  ;; ...)`) so that :biff.fx/now gets injected.
  [{:biff.fx/keys [now]} subscriber]
  (let [now (or now (tick/instant))]
    {:subscriber/confirmation-token-active
     (boolean
      (when-let [triggered-at
                 (:subscriber/confirmation-triggered-at subscriber)]
        (tick/> (tick/>> triggered-at (tick/of-hours 24)) now)))}))

(def module
  {:biff.graph/resolvers
   [by-publication-email
    by-confirmation-token
    active
    confirmation-token-active]})
