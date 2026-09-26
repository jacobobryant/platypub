(ns com.platypub.work.suppress-bounces
  (:require [com.biffweb.fx :refer [defmachine]]
            [com.platypub.lib.subscriber :as subscriber]
            [com.platypub.lib.schedule :as schedule]
            [tick.core :as tick]))

(defn- request
  [{:biff.fx/keys    [now]
    :mailersend/keys [api-key base-url domain-id]}]
  {:method           :get
   :url              (str (or base-url "https://api.mailersend.com")
                          "/v1/activity/" domain-id)
   :headers          {"Authorization" (str "Bearer " (force api-key))}
   :query-params     {:date_from (tick/long (tick/<< now (tick/of-hours 24)))
                      :date_to   (tick/long now)
                      :limit     100
                      "event[]"  ["hard_bounced" "spam_complaints"]}
   :throw-exceptions false
   :as               :json})

(defn- response-emails
  [response]
  (if (and (:status response) (< (:status response) 400))
    (set (keep #(some-> (get-in % [:email :recipient :email])
                        subscriber/normalize-email)
               (get-in response [:body :data])))
    #{}))

(defmachine suppress-bounces
  :start
  (fn [ctx]
    (let [request (request ctx)]
      {:emails       #{}
       :page-count   0
       :request      request
       :response     [:biff.fx/http request]
       :biff.fx/next :page}))

  :page
  (fn [_ctx {:keys [emails page-count request response]}]
    (let [emails     (into emails (response-emails response))
          page-count (inc page-count)
          next-url   (get-in response [:body :links :next])]
      (if (and next-url (< page-count 1000))
        (let [request (assoc request :url next-url :query-params nil)]
          {:emails       emails
           :page-count   page-count
           :request      request
           :response     [:biff.fx/http request]
           :biff.fx/next :page})
        {:emails       emails
         :biff.fx/next :finish})))

  :finish
  (fn [_ctx {:keys [emails]}]
    (if (seq emails)
      [:biff.sqlite.fx/execute
       {:update :subscriber
        :set    {:subscriber/suppressed true}
        :where  [:in :subscriber/email emails]}]
      {:biff.fx/return nil})))

(def module
  {:biff.background/tasks
   [{:schedule (schedule/every (tick/of-hours 6))
     :task     suppress-bounces}]})
