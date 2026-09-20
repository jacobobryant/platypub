(ns com.platypub.work.suppress-bounces
  (:require [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.subscriber :as subscriber]
            [com.platypub.lib.schedule :as schedule]
            [tick.core :as tick]))

(defpipeline suppress-bounces
  (fn [{:biff.fx/keys    [now]
        :mailersend/keys [api-key base-url]}]
    [:biff.fx/http
     {:method           :get
      :url              (str (or base-url "https://api.mailersend.com")
                             "/v1/activity/email")
      :headers          {"Authorization" (str "Bearer " (force api-key))}
      :query-params     {:date_from (str (tick/<< now (tick/of-hours 24)))
                         :date_to   (str now)
                         :event     ["hard_bounced" "spam_complaint"]}
      :throw-exceptions false
      :as               :json}])

  (fn [_ctx response]
    (let [events
          (if (and (:status response) (< (:status response) 400))
            (get-in response [:body :data])
            [])

          emails
          (set (keep #(some-> (or (:email %)
                                  (get-in % [:recipient :email]))
                              subscriber/normalize-email)
                     events))]
      (if (seq emails)
        [:biff.sqlite.fx/execute
         {:update :subscriber
          :set    {:subscriber/suppressed true}
          :where  [:in :subscriber/email emails]}]
        {:biff.fx/return nil}))))

(def module
  {:biff.background/tasks
   [{:schedule (schedule/every (tick/of-hours 6))
     :task     suppress-bounces}]})
