(ns com.platypub.schema
  (:require [malli.core :as malc]
            [malli.registry :as malr]))

(def schema
  {:user/id :uuid
   :user/email :string
   :user/foo :string
   :user/bar :string
   :user [:map {:closed true}
          [:xt/id :user/id]
          :user/email
          [:user/foo {:optional true}]
          [:user/bar {:optional true}]]

   :msg/id :uuid
   :msg/user :user/id
   :msg/text :string
   :msg/sent-at inst?
   :msg [:map {:closed true}
         [:xt/id :msg/id]
         :msg/user
         :msg/text
         :msg/sent-at]

   :post/id :uuid
   :post/html :string
   :post/published-at inst?
   :post/edited-at inst?
   :post [:map {:closed true}
          [:xt/id :post/id]
          :post/html
          :post/published-at
          :post/edited-at]
   })

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})
