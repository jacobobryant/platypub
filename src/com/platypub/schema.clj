(ns com.platypub.schema)

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
          [:xt/id           :site/id]
          [:site/user       :user/id]
          [:site/netlify-id :string]
          [:site/url        :string]
          [:site/title      :string]
          [:site/theme      :string]]

   :item/id :uuid
   ;; todo only allow additional keys if they start with a certain prefix
   :item [:map
          [:xt/id      :item/id]
          [:item/user  :user/id]
          [:item/sites [:set :site/id]]]

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
                          :list/reply-to]
               :optional [:list/mailing-address
                          :list/sites
                          ;; deprecated
                          :list/theme
                          :list/tags]})})

(def plugin
  {:schema schema})
