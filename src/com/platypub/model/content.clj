(ns com.platypub.model.content
  (:require [cheshire.core :as json]
            [com.biffweb.graph :refer [defresolver]])
  (:import [java.nio.charset StandardCharsets]))

(defresolver post-content
  {:input  [:post/content-id]
   :output [{:post/content [:content/id]}]}
  [_ctx {:post/keys [content-id]}]
  {:post/content {:content/id content-id}})

(defresolver send-content
  {:input  [:send/content-id]
   :output [{:send/content [:content/id]}]}
  [_ctx send]
  {:send/content {:content/id (:send/content-id send)}})

(defresolver blob
  {:input  [:content/id]
   :output [:content/blob]}
  (fn [_ctx content]
    {:object [:platypub.fx/get-object (:content/id content)]})
  (fn [_ctx {:keys [object]}]
    {:content/blob (:body object)}))

(defresolver values
  {:input  [:content/blob]
   :output [:content/string
            {:content/json [:*]}
            :content/html
            :content/text]}
  [_ctx content]
  (let [string-value (String. ^bytes (:content/blob content)
                              StandardCharsets/UTF_8)
        json-value   (json/parse-string string-value true)]
    {:content/string string-value
     :content/json   json-value
     :content/html   (:html json-value)
     :content/text   (:text json-value)}))

(def module
  {:biff.graph/resolvers
   [post-content send-content blob values]})
