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
                          :post/status]})

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

   :site/id :uuid
   :site/user :user/id
   :site/url :string
   :site/title :string
   :site/description :string
   :site/image :string
   :site/tag :string
   :site/theme :string
   :site/redirects :string
   :site/netlify-id :string
   :site/custom-config map?
   :site (doc {:id :site/id
               :required [:site/user
                          :site/url
                          :site/title
                          :site/description
                          :site/image
                          :site/tag
                          :site/theme
                          :site/redirects
                          :site/netlify-id]
               :optional [:site/custom-config]})

   :list/id :uuid
   :list/user :user/id
   :list/address :string
   :list/title :string
   :list/theme :string
   :list/reply-to :string
   :list/tags [:sequential :string]
   :list/mailing-address :string
   :list (doc {:id :list/id
               :required [:list/user
                          :list/address
                          :list/title
                          :list/theme
                          :list/reply-to]
               :optional [:list/tags
                          :list/mailing-address]})})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})
