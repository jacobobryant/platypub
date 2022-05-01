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
   :user/foo :string
   :user/bar :string
   :user [:map {:closed true}
          [:xt/id :user/id]
          :user/email
          [:user/foo {:optional true}]
          [:user/bar {:optional true}]]

   :post/id :uuid
   :post/title :string
   :post/html :string
   :post/published-at inst?
   :post/edited-at inst?
   :post/slug :string
   :post/description :string
   :post/tags [:sequential :string]
   :post/authors [:sequential :author/id]
   :post/canonical :string
   :post/image :string
   :post/status [:enum :draft :published]
   :post (doc {:id :post/id
               :required [:post/title
                          :post/html
                          :post/published-at
                          :post/edited-at
                          :post/slug
                          :post/description
                          :post/tags
                          :post/authors
                          :post/canonical
                          :post/image
                          :post/status]})

   :author/id :uuid
   :author/name :string
   :author/image :string
   :author/url :string
   :author (doc {:id :author/id
                 :required [:author/name
                            :author/image
                            :author/url]})

   :image/id :uuid
   :image/url :string
   :image/filename :string
   :image/uploaded-at inst?
   :image (doc {:id :image/id
                :required [:image/url
                           :image/filename
                           :image/uploaded-at]})

   :site/id :uuid
   :site/url :string
   :site/title :string
   :site/description :string
   :site/image :string
   :site/tag :string
   :site/theme :qualified-symbol
   :site/redirects :string
   :site/netlify-id :string
   :site (doc {:id :site/id
               :required [:site/url
                          :site/title
                          :site/description
                          :site/image
                          :site/tag
                          :site/theme
                          :site/redirects
                          :site/netlify-id]})})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})
