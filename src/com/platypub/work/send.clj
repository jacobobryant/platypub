(ns com.platypub.work.send
  (:require [clojure.string :as str]
            [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.email :as email]
            [com.platypub.lib.tokens :as tokens]
            [com.platypub.routes :as routes]
            [tick.core :as tick]))

(defn- unsubscribe-url
  [{:keys [platypub/unsubscribe-secret platypub/base-url]} now subscriber]
  (let [token (tokens/process
               :sign
               (force unsubscribe-secret)
               {:subscriber-id (:subscriber/id subscriber)
                :email         (:subscriber/email subscriber)
                :exp           (tick/long (tick/>> now (tick/of-days 30)))})]
    (str base-url (routes/unsubscribe token))))

(defn- delivery-effects
  [ctx now {send-record :send
            :keys       [publication
                         owner
                         content
                         subscriber
                         attempt-id
                         unsubscribe-url]}]
  (let [content (update-vals
                 content
                 #(str/replace %
                               email/unsubscribe-placeholder
                               unsubscribe-url))]
    [[:biff.sqlite.fx/execute-tx
      [{:insert-into :send-attempt

        :values
        [{:send-attempt/id            attempt-id
          :send-attempt/send-id       (:send/id send-record)
          :send-attempt/subscriber-id (:subscriber/id subscriber)}]}
       {:update :send
        :set    {:send/progress-at now}
        :where  [:= :send/id (:send/id send-record)]}]]
     [:biff.fx/http
      (email/request
       ctx
       {:to              (:subscriber/email subscriber)
        :subject         (:send/subject send-record)
        :html            (:html content)
        :text            (:text content)
        :unsubscribe-url unsubscribe-url
        :from-name       (:publication/title publication)
        :reply-to        (:user/email owner)})]
     [:biff.fx/sleep 50]]))

(defpipeline send-consumer
  (fn [{:biff.background/keys [job]}]
    [:biff.graph.fx/query
     {:send/id (:send-id job)}
     [:send/id
      :send/status
      :send/publication-id
      :send/content-id
      :send/started-at]])

  (fn [_ctx result]
    (if (= :send.status/pending (:send/status result))
      {:send [:biff.graph.fx/query
              {:send/id (:send/id result)}
              [:send/id :send/status :send/publication-id
               :send/content-id :send/started-at
               {:send/publication
                [:publication/id :publication/title :publication/user-id
                 {:publication/user [:user/id :user/email]}]}
               {:send/content [:content/id :content/data]}
               {:send/attempts [:send-attempt/subscriber-id]}
               {:send/delivery-subscribers
                [:subscriber/id :subscriber/email]}]]}
      {:biff.fx/return nil}))

  (fn [_ctx {:keys [send] :as data}]
    (if data
      (let [publication (:send/publication send)
            content     (:send/content send)
            attempted   (:send/attempts send)]
        (assoc data
               :send send
               :publication publication
               :content (:content/data content)
               :owner (:publication/user publication)
               :attempted (set (map :send-attempt/subscriber-id attempted))
               :subscribers (:send/delivery-subscribers send)))
      {:biff.fx/return nil}))

  (fn [{:biff.fx/keys [now random-uuid7-seq] :as ctx}
       {send-record :send
        :keys       [publication content owner attempted subscribers]
        :as         data}]
    (if data
      (let [deliveries
            (mapv (fn [subscriber attempt-id]
                    {:send            send-record
                     :publication     publication
                     :owner           owner
                     :content         content
                     :subscriber      subscriber
                     :attempt-id      attempt-id
                     :now             now
                     :unsubscribe-url (unsubscribe-url ctx now subscriber)})
                  (remove #(attempted (:subscriber/id %)) subscribers)
                  random-uuid7-seq)]
        {:biff.fx/seq
         (concat
          (mapcat #(delivery-effects ctx now %) deliveries)
          [{:_finish [:biff.sqlite.fx/execute
                      {:update :send
                       :set    {:send/status      [:lift :send.status/finished]
                                :send/progress-at now}
                       :where  [:= :send/id (:send/id send-record)]}]}])})
      {:biff.fx/return nil})))

(def module
  {:biff.background/queues
   {:platypub/send {:consumer send-consumer :n-threads 1}}})
