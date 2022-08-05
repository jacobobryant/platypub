(ns com.platypub.repl
  (:require [com.biffweb :as biff :refer [q]]
            [com.platypub.netlify :as netlify]
            [clj-http.client :as http]
            [xtdb.api :as xt]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(defn migrate! [email]
  (let [{:keys [biff/db biff.xtdb/node]} (get-sys)
        user-id (biff/lookup-id db :user/email email)
        tx (for [[lookup-key user-key] [[:post/title :post/user]
                                        [:image/url :image/user]
                                        [:site/url :site/user]
                                        [:list/title :list/user]]
                 [doc] (xt/q db
                             {:find '[(pull doc [*])]
                              :where [['doc lookup-key]]})]
             [::xt/put (-> doc
                           (assoc user-key user-id)
                           (dissoc :post/authors))])]
    (xt/submit-tx node tx)))

;; cleanup
;; - remove anything with :post/title
;; - remove keys from sites: custom-config, description, image, redirects, tag


(defn next-uuid [uuid]
  (java.util.UUID/nameUUIDFromBytes (.getBytes (str uuid))))

(defn migrate-items! []
  (let [{:keys [biff/db] :as sys} (get-sys)
        sites (q db
                 '{:find (pull site [*])
                   :where [[site :site/title]]})
        posts (q db
                 '{:find (pull post [*])
                   :where [[post :post/title]]})
        site-tx (for [site sites]
                  (merge
                   {:db/doc-type :site
                    :db/op :update
                    :xt/id (:xt/id site)}
                   (-> site
                       (select-keys [:site/description
                                     :site/image
                                     :site/redirects])
                       (biff/select-ns-as 'site 'site.custom.com.platypub.site))
                   (biff/select-ns-as (:site/custom-config site)
                                      'com.platypub 'site.custom.com.platypub.site)))
        tag->site-id (->> sites
                          (map (juxt :site/tag :xt/id))
                          (into {}))
        item-tx (for [post posts]
                  (merge
                   {:db/doc-type :item
                    :xt/id (next-uuid (:xt/id post))
                    :item/user (:post/user post)
                    :item/sites (->> (:post/tags post)
                                     (keep tag->site-id)
                                     (concat (:post/sites post))
                                     set)
                    :item.custom.com.platypub.post/draft (= :draft (:post/status post))}
                   (-> post
                       (select-keys [:post/title
                                     :post/html
                                     :post/description
                                     :post/tags
                                     :post/image])
                       (biff/select-ns-as 'post 'item.custom.com.platypub.post))
                   (if (contains? (set (:post/tags post)) "page")
                     {:item.custom.com.platypub.page/path (str "/" (:post/slug post))}
                     (-> post
                         (select-keys [:post/canonical
                                       :post/slug
                                       :post/published-at])
                         (biff/select-ns-as 'post 'item.custom.com.platypub.post)))))]
    (biff/submit-tx sys
      (concat site-tx item-tx))))

(comment

  ;; If I eval (biff/refresh) with Conjure, it starts sending stdout to Vim.
  ;; fix-print makes sure stdout keeps going to the terminal.
  (biff/fix-print (biff/refresh))

  (let [{:keys [biff/db] :as sys} (get-sys)]
    )

  (sort (keys @biff/system)))
