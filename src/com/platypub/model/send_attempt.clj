(ns com.platypub.model.send-attempt
  (:require [com.biffweb.graph :refer [defresolver]]))

(defresolver for-send
  {:input  [:send/id]
   :output [{:send/attempts [:send-attempt/id]}]}
  (fn [_ctx {:send/keys [id]}]
    {:send/attempts
     [:biff.sqlite.fx/execute
      {:select [:send-attempt/id]
       :from   :send-attempt
       :where  [:= :send-attempt/send-id id]}]}))

(def module
  {:biff.graph/resolvers [for-send]})
