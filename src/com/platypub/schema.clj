(ns com.platypub.schema
  (:require [malli.core :as malc]
            [malli.registry :as malr]))

(defn doc [{:keys [id required optional]}]
  (vec (concat [:map {:closed true}
                [:xt/id id]]
               required
               (for [k optional]
                 [k {:optional true}]))))

(def schema
  {:user/id :uuid
   :user/email :string
   :user (doc {:id :user/id
               :required [:user/email]})

   :site/id :uuid
   :site [:map
          [:xt/id :site/id]
          [:site/user :user/id]
          [:site/netlify-id :string]
          [:site/url :string]
          [:site/title :string]
          [:site/theme :string]]

   :item/id :uuid
   ;; todo only allow additional keys if they start with a certain prefix
   :item [:map
          [:xt/id :item/id]
          [:item/user :user/id]
          [:item/sites [:set :site/id]]]

   :post/id :uuid
   :post/user :user/id
   :post/title :string
   :post/html :string
   :post/published-at inst?
   :post/edited-at inst?
   :post/slug :string
   :post/description :string
   :post/tags [:sequential :string]
   :post/canonical :string
   :post/image :string
   :post/status [:enum :draft :published]
   :post/sites [:vector :site/id]
   :post (doc {:id :post/id
               :required [:post/user
                          :post/title
                          :post/html
                          :post/published-at
                          :post/edited-at
                          :post/slug
                          :post/description
                          :post/tags
                          :post/canonical
                          :post/image
                          :post/status]
               :optional [:post/sites]})

   :image/id :uuid
   :image/user :user/id
   :image/url :string
   :image/filename :string
   :image/uploaded-at inst?
   :image (doc {:id :image/id
                :required [:image/user
                           :image/url
                           :image/filename
                           :image/uploaded-at]})

   :list/id :uuid
   :list/user :user/id
   :list/address :string
   :list/title :string
   :list/theme :string
   :list/reply-to :string
   :list/tags [:sequential :string]
   :list/mailing-address :string
   :list/sites [:vector :site/id]
   :list (doc {:id :list/id
               :required [:list/user
                          :list/address
                          :list/title
                          :list/theme
                          :list/reply-to]
               :optional [:list/tags
                          :list/mailing-address
                          :list/sites]})})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})
