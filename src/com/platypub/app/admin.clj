(ns com.platypub.app.admin
  (:require [clojure.string :as str]
            [clojure.data.csv :as csv]
            [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.middleware :as mid]
            [com.platypub.lib.tab :as tab]
            [com.platypub.lib.ui :as ui]
            [com.platypub.lib.subscriber :as subscriber]
            [com.platypub.routes :as routes])
  (:import [java.io StringReader]))

(defpath root-path "")
(defpath admin-user-tier-path "/app/admin/users/:user-id/tier")
(defpath admin-publication-import-path
  "/app/admin/publications/:publication-id/import")

(defn- tier-signal
  [user-id]
  (keyword "request" (str "tier-" user-id)))

(defn wrap-admin
  [handler]
  (mid/wrap-app-access
   (fn [{:keys [platypub/user], :as request}]
     (if (= :user.tier/admin (:user/tier user))
       (handler request)
       {:status 403}))))

(defpipeline dashboard
  [:biff.graph.fx/query
   [{:request/admin-publication-search [:publication/search]}
    {:global/users [:user/id :user/email :user/tier]}
    {:request/admin-publications [:publication/id :publication/title]}]]

  (fn [request result]
    (let [q
          (get-in result
                  [:request/admin-publication-search :publication/search])

          users (:global/users result)

          publications (:request/admin-publications result)]
      (ui/app-page
       request
       [:main
        {:class ["mx-auto max-w-5xl p-6"]}
        [:a
         {:href (routes/app), :class ["text-primary"]}
         "← Publications"]
        [:h1
         {:class ["my-4 text-3xl font-bold"]}
         "Admin dashboard"]
        [:h2 {:class ["mb-2 text-xl font-semibold"]} "Users"]
        [:div
         {:class ["mb-8 divide-y rounded border"]}
         (for [row users]
           [:form
            {:data-on:submit "@post(el.dataset.action)",
             :data-action    (admin-user-tier-path (:user/id row)),

             :data-signals__ifmissing
             (datastar/signals-json
              {(tier-signal (:user/id row)) (name (:user/tier row))}),

             :class ["flex items-center gap-3 p-3"]}
            [:span {:class ["flex-1"]} (:user/email row)]
            [:select
             {:data-bind (datastar/signal-name
                          (tier-signal (:user/id row))),
              :class     ["rounded border p-2"]}
             (for [tier (cond-> [:free :admin]
                          (= :user.tier/waitlist
                             (:user/tier row))
                          (conj :waitlist))]
               [:option
                {:value    (name tier),
                 :selected (= (keyword "user.tier"
                                       (name tier))
                              (:user/tier row))}
                (name tier)])]
            [:button
             {:class ["rounded border px-3 py-2"]}
             "Save"]])]
        [:h2
         {:class ["mb-2 text-xl font-semibold"]}
         "Import subscribers"]
        [:form
         {:data-on:submit "@post(el.dataset.action)",
          :data-action    (routes/app-admin),

          :data-signals__ifmissing
          (datastar/signals-json {:publication/search q}),

          :class ["mb-4 flex gap-2"]}
         [:input
          {:data-bind   (datastar/signal-name :publication/search),
           :placeholder "Search title or owner email",
           :class       ["flex-1 rounded border p-2"]}]
         [:button {:class ["rounded border px-4"]} "Search"]]
        (for [publication publications]
          [:form
           {:data-on:submit "@post(el.dataset.action)",

            :data-action
            (admin-publication-import-path (:publication/id publication)),

            :data-signals__ifmissing (datastar/signals-json {:request/csv nil}),

            :class ["mb-3 flex items-center gap-3 rounded border p-3"]}
           [:span
            {:class ["flex-1"]}
            (:publication/title publication)]
           [:input
            {:type      "file",
             :data-bind (datastar/signal-name :request/csv),
             :accept    ".csv,text/csv",
             :required  true}]
           [:button
            {:class ["rounded bg-primary px-3 py-2 text-white"]}
            "Import"]])]))))

(defpipeline update-search
  [:biff.graph.fx/query
   [{:request/admin-publication-search [:publication/search]}
    {:request/tab [[:? :tab/admin-publication-search]]}]]

  (fn [{:keys [biff.datastar/tab-id]} result]
    {:_search
     [:biff.sqlite.fx/execute
      (tab/write-statement
       tab-id
       (:request/tab result)
       {:tab/admin-publication-search
        (get-in result
                [:request/admin-publication-search :publication/search])})]

     :biff.fx/return {:status 204}}))

(defpipeline set-tier
  [:biff.graph.fx/query
   [{:request/admin-user-tier [:user/id :user/tier :request/tier]}]]

  (fn [_ctx result]
    (let [{:user/keys [id] :request/keys [tier]}
          (:request/admin-user-tier result)

          tier (some->> tier name (keyword "user.tier"))]
      (if (and (#{:user.tier/free :user.tier/admin
                  :user.tier/waitlist}
                tier)
               (or (= :user.tier/waitlist
                      (:user/tier (:request/admin-user-tier result)))
                   (not= :user.tier/waitlist tier)))
        {:_write
         [:biff.sqlite.fx/authorized-write
          {:update :user,
           :set    {:user/tier [:lift tier]},
           :where  [:= :user/id id]}]

         :biff.fx/return {:status 204}}
        {:status 204}))))

(defn- csv-emails
  [contents]
  (let [rows (with-open [reader (StringReader. (or contents ""))]
               (doall (csv/read-csv reader)))

        header
        (mapv (comp str/lower-case str/trim)
              (first rows))

        index (.indexOf header "email")]
    (if (neg? index)
      []
      (->> (rest rows)
           (keep #(get % index))
           (map subscriber/normalize-email)
           (filter subscriber/valid-email?)
           distinct))))

(defpipeline import-subscribers
  [:biff.graph.fx/query
   [{:request/admin-publication-import [:publication/id :request/csv]}]]

  (fn [_ctx result]
    (let [{:publication/keys [id] :request/keys [csv]}
          (:request/admin-publication-import result)]
      {:publication-id id
       :csv            csv
       :existing       [:biff.graph.fx/query
                        {:publication/id id}
                        [{:publication/subscribers [:subscriber/email]}]]}))

  (fn [{:biff.fx/keys [now random-uuid7-seq]}
       {:keys [publication-id csv existing]}]
    (let [existing
          (set (map :subscriber/email
                    (:publication/subscribers existing)))

          emails (remove existing (csv-emails csv))

          rows
          (mapv (fn [email id]
                  {:subscriber/id                   id,
                   :subscriber/email                email,
                   :subscriber/publication-id       publication-id,
                   :subscriber/subscribed-at        now,
                   :subscriber/require-confirmation false})
                emails
                random-uuid7-seq)]
      {:_write         (when (seq rows)
                         [:biff.sqlite.fx/authorized-write
                          {:insert-into :subscriber, :values rows}])
       :biff.fx/return {:status 204}})))

(def module
  {:biff.ring/routes [[(root-path)
                       {:middleware [wrap-admin]}
                       [(routes/app-admin)
                        {:get dashboard, :post update-search}]
                       [(admin-user-tier-path) {:post set-tier}]
                       [(admin-publication-import-path)
                        {:post import-subscribers}]]]})
