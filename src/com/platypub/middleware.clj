(ns com.platypub.middleware
  (:require [clojure.set :as set]
            [com.platypub.ui :as ui]
            [com.platypub.util :as util]
            [xtdb.api :as xt]))

(defn ensure-owner [user doc]
  (when (some #(= (:xt/id user) (get doc %)) [:item/user :list/user :site/user :image/user])
    doc))

(defn wrap-signed-in [handler]
  (fn [{:keys [biff/db session path-params] :as req}]
    (let [user (xt/entity db (:uid session))
          sites (delay (util/q-sites db user))
          site (delay (->> @sites
                           (filter #(= (:site-id path-params) (str (:xt/id %))))
                           first))
          item-spec (delay (->> (:site.config/items @site)
                                (filter #(= (:item-slug path-params) (:slug %)))
                                first))
          item (delay (ensure-owner
                       user
                       (xt/entity db (parse-uuid (:item-id path-params)))))
          lst (delay (ensure-owner
                      user
                      (xt/entity db (parse-uuid (:list-id path-params)))))
          params (-> {:site-id site
                      :item-slug item-spec
                      :item-id item
                      :list-id lst}
                     (select-keys (keys path-params))
                     (set/rename-keys {:site-id :site
                                       :item-slug :item-spec
                                       :item-id :item
                                       :list-id :list})
                     (update-vals force))]
      (cond
       (nil? user) {:status 303
                    :headers {"location" "/"}}
       (some nil? (vals params)) ui/not-found-response
       :else (handler (merge req {:user user :sites @sites} params))))))
