(ns com.platypub.app.auth
  (:require [com.biffweb.authenticate :as biff.auth]
            [com.biffweb.fx :as fx :refer [defpipeline]]
            [com.platypub.lib.email :as email]
            [com.platypub.routes :as routes]))

(defpipeline get-user-id
  (fn [_ctx email]
    [:biff.graph.fx/query
     {:user/email email}
     [:user/id]])

  (fn [_ctx result]
    (:user/id result)))

(defpipeline create-user
  (fn [_ctx {:keys [email]}]
    {:email email
     :users [:biff.graph.fx/query [:global/user-count]]})

  (fn [{:biff.fx/keys  [now random-uuid7-seq],
        :platypub/keys [waitlist-enabled]}
       {:keys [email users]}]
    (let [[new-user-id] random-uuid7-seq]
      {:user-id new-user-id

       :_write
       [:biff.sqlite.fx/execute
        {:insert-into :user,

         :values
         [{:user/id        new-user-id,
           :user/email     email,
           :user/joined-at now,

           :user/tier
           [:lift
            (cond
              (zero? (:global/user-count users)) :user.tier/admin

              waitlist-enabled :user.tier/waitlist
              :else            :user.tier/free)]}],

         :on-conflict   [:user/email],
         :do-update-set [:user/email],
         :returning     [:user/id]}]}))

  (fn [_ctx {:keys [user-id]}]
    user-id))

(def module
  (biff.auth/module
   (merge {:biff.auth/app-path             (routes/app),
           :biff.auth/app-name             "Platypub",
            ;; Uncomment to change the default color:
            ;:biff.auth/primary-color        "#4F46E5"
           :biff.auth/send-email           #'email/send-email,
           :biff.auth/get-user-id          #'get-user-id,
           :biff.auth/create-user          #'create-user,
            ;; We're using com.biffweb.ring/wrap-csrf-protection.
           :biff.auth/skip-csrf-protection true}
          biff.auth/turnstile-config)))
