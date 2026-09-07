(ns com.platypub.app.auth
  (:require [com.biffweb.authenticate :as biff.auth]
            [com.biffweb.fx :as fx :refer [defpipeline]]
            [com.platypub.lib.email :as email]
            [com.platypub.routes :as routes]))

(defpipeline get-user-id
  (fn [_ctx email]
    [:biff.sqlite.fx/execute
     {:select [:user/id]
      :from   :user
      :where  [:= :user/email email]}])

  (fn [_ctx result]
    (-> result first :user/id)))

(defpipeline create-user
  (fn [{:biff.fx/keys [now random-uuid7-seq]} {:keys [email]}]
    (let [[new-user-id] random-uuid7-seq]
      [:biff.sqlite.fx/execute
       {:insert-into   :user
        :values        [{:user/id        new-user-id
                         :user/email     email
                         :user/joined-at now}]
        :on-conflict   [:user/email]
        :do-update-set [:user/email]
        :returning     [:user/id]}]))

  (fn [_ctx result]
    (-> result first :user/id)))

(def module
  (biff.auth/module
   (merge
    {:biff.auth/app-path             (routes/app)
     :biff.auth/app-name             "My Application"
     ;; Uncomment to change the default color:
     ;:biff.auth/primary-color        "#4F46E5"
     :biff.auth/send-email           #'email/send-email
     :biff.auth/get-user-id          #'get-user-id
     :biff.auth/create-user          #'create-user
     ;; We're using com.biffweb.ring/wrap-csrf-protection.
     :biff.auth/skip-csrf-protection true}
    biff.auth/turnstile-config)))
