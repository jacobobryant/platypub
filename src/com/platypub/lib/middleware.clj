(ns com.platypub.lib.middleware
  (:require [com.biffweb.datastar :as datastar]
            [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.ui :as ui]
            [com.platypub.routes :as routes]))

(defpipeline app-access
  (fn [{:keys [session]}]
    [:biff.graph.fx/query
     {:user/id (:uid session)}
     [:user/id
      :user/tier
      {:global/first-user [:user/id]}]])

  (fn [{:platypub/keys [waitlist-enabled]} result]
    (let [user       result
          first-user (get-in result [:global/first-user :user/id])]
      (if (:user/tier user)
        {:user user}
        (let [tier (if (= (:user/id user) first-user)
                     :user.tier/admin
                     (if waitlist-enabled
                       :user.tier/waitlist
                       :user.tier/free))]
          {:user (assoc user :user/tier tier)

           :_write
           [:biff.sqlite.fx/execute
            {:update :user
             :set    {:user/tier [:lift tier]}
             :where  [:= :user/id (:user/id user)]}]}))))

  (fn [_ctx {:keys [user]}]
    user))

(defn- wrap-signed-in
  [handler]
  (fn [{:keys [session], :as ctx}]
    (if (some? (:uid session))
      (handler ctx)
      {:status  303,
       :headers {"location" (routes/signin)}})))

(defn wrap-app-access
  [handler]
  (wrap-signed-in
   (fn [ctx]
     (let [user (app-access ctx)]
       (if (= :user.tier/waitlist (:user/tier user))
         (ui/app-page
          ctx
          [:main
           {:class ["mx-auto max-w-xl p-10 text-center"]}
           [:h1
            {:class ["text-2xl font-bold"]}
            "You're on the waitlist"]
           [:p
            {:class ["mt-3"]}
            "We'll notify you when you have access to Platypub."]
           [:form
            {:data-on:submit          "@post(el.dataset.action)"
             :data-action             (routes/signout)
             :data-signals__ifmissing (datastar/signals-json {})
             :class                   ["mt-6"]}
            [:button
             {:class ["text-primary"]}
             "Sign out"]]])
         (handler (assoc ctx :platypub/user user)))))))
