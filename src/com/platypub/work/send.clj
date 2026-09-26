(ns com.platypub.work.send
  (:require [cheshire.core :as json]
            [com.biffweb.fx :refer [defpipeline]]
            [com.platypub.lib.email :as email]
            [com.platypub.lib.tokens :as tokens]
            [com.platypub.routes :as routes]
            [tick.core :as tick])
  (:import [java.nio.charset StandardCharsets]))

(def max-bulk-payload-size (* 50 1000 1000))

(defn- bulk-limit
  [plan]
  (if (#{:free :sandbox} plan) 5 500))

(defn- bulk-delay-ms
  [plan]
  (case plan
    :enterprise 1000
    (:professional :professional-trial) 2000
    :starter 4000
    6000))

(defn- message-size
  [ctx delivery]
  (alength (.getBytes (json/generate-string
                       (email/message ctx (:email delivery)))
                      StandardCharsets/UTF_8)))

(defn- bulk-batches
  [ctx deliveries]
  (let [limit (bulk-limit (:mailersend/plan ctx))]
    (reduce
     (fn [batches delivery]
       (let [size    (message-size ctx delivery)
             current (peek batches)]
         (if (and current
                  (< (count (:deliveries current)) limit)
                  (< (+ (:size current) size 1) max-bulk-payload-size))
           (conj (pop batches)
                 (-> current
                     (update :deliveries conj delivery)
                     (update :size + size 1)))
           (conj batches {:deliveries [delivery]
                          :size       (+ size 2)}))))
     []
     deliveries)))

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
  [ctx deliveries]
  (let [send-record (:send (first deliveries))]
    [[:biff.sqlite.fx/execute-tx
      [{:insert-into :send-attempt
        :values      (mapv (fn [{:keys [attempt-id subscriber]}]
                             {:send-attempt/id      attempt-id
                              :send-attempt/send-id (:send/id send-record)

                              :send-attempt/subscriber-id
                              (:subscriber/id subscriber)})
                           deliveries)}
       {:update :send
        :set    {:send/progress-at [:* 1000 [:unixepoch]]}
        :where  [:= :send/id (:send/id send-record)]}]]
     [:platypub.fx/http-with-backoff
      (email/bulk-request ctx (mapv :email deliveries))]]))

(defpipeline send-consumer
  (fn [{:biff.background/keys [job]}]
    [:biff.graph.fx/query
     {:send/id (:send-id job)}
     [:send/id
      :send/status
      :send/publication-id
      :send/content-id
      :send/started-at
      :send/from-name
      :send/reply-to
      :send/subject]])

  (fn [_ctx result]
    (if (= :send.status/pending (:send/status result))
      {:send [:biff.graph.fx/query
              {:send/id (:send/id result)}
              [:send/id :send/status :send/publication-id
               :send/content-id :send/started-at :send/from-name :send/reply-to
               :send/subject
               {:send/content [:content/id :content/html :content/text]}
               {:send/attempts [:send-attempt/subscriber-id]}
               {:send/delivery-subscribers
                [:subscriber/id :subscriber/email]}]]}
      {:biff.fx/return nil}))

  (fn [{:biff.fx/keys [now random-uuid7-seq] :as ctx}
       {:keys [send] :as data}]
    (if data
      (let [content (:send/content send)

            attempted
            (set (map :send-attempt/subscriber-id (:send/attempts send)))

            subscribers (:send/delivery-subscribers send)

            deliveries
            (mapv (fn [subscriber attempt-id]
                    {:send       send
                     :subscriber subscriber
                     :attempt-id attempt-id
                     :email      {:to      (:subscriber/email subscriber)
                                  :subject (:send/subject send)
                                  :html    (:content/html content)
                                  :text    (:content/text content)

                                  :unsubscribe-url
                                  (unsubscribe-url ctx now subscriber)

                                  :from-name (:send/from-name send)
                                  :reply-to  (:send/reply-to send)}})
                  (filterv #(not (attempted (:subscriber/id %))) subscribers)
                  random-uuid7-seq)

            batches (bulk-batches ctx deliveries)

            effects
            (into []
                  (mapcat
                   (fn [index {:keys [deliveries]}]
                     (cond-> (mapv (fn [effect] {:_delivery effect})
                                   (delivery-effects ctx deliveries))
                       (< index (dec (count batches)))
                       (conj {:_throttle
                              [:biff.fx/sleep
                               (bulk-delay-ms (:mailersend/plan ctx))]})))
                   (range)
                   batches))]
        {:biff.fx/seq
         (conj
          effects
          {:_finish
           [:biff.sqlite.fx/execute
            {:update :send
             :set    {:send/status [:lift :send.status/finished]

                      :send/progress-at
                      [:* 1000 [:unixepoch]]}

             :where [:= :send/id (:send/id send)]}]})})
      {:biff.fx/return nil})))

(def module
  {:biff.background/queues
   {:platypub/send {:consumer send-consumer :n-threads 1}}})
