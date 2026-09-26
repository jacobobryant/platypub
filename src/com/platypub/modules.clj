(ns com.platypub.modules
  (:require [com.biffweb.background :as biff.background]
            [com.biffweb.config :as biff.config]
            [com.biffweb.datastar :as biff.datastar]
            [com.biffweb.fx :as biff.fx]
            [com.biffweb.graph :as biff.graph]
            [com.biffweb.ring :as biff.ring]
            [com.platypub.app.admin :as app.admin]
            [com.platypub.app.auth :as app.auth]
            [com.platypub.app.biff-admin :as app.biff-admin]
            [com.platypub.app.confirm-subscription :as app.confirm-subscription]
            [com.platypub.app.landing :as app.landing]
            [com.platypub.app.mock-mailersend
             :as
             app.mock-mailersend]
            [com.platypub.app.publications :as app.publications]
            [com.platypub.app.publications.publication :as app.publication]
            [com.platypub.app.publications.publication.send
             :as
             app.publication-send]
            [com.platypub.app.publications.publication.settings
             :as
             app.publication-settings]
            [com.platypub.app.publications.publication.subscribers
             :as
             app.publication-subscribers]
            [com.platypub.app.subscription :as app.subscription]
            [com.platypub.app.unsubscribe :as app.unsubscribe]
            [com.platypub.api.mock-cdn :as api.mock-cdn]
            [com.platypub.api.mock-mailersend :as api.mock-mailersend]
            [com.platypub.fx :as platypub.fx]
            [com.platypub.modules.minio :as minio]
            [com.platypub.lib.ui :as lib.ui]
            [com.platypub.model.feed :as model.feed]
            [com.platypub.model.content :as model.content]
            [com.platypub.model.global :as model.global]
            [com.platypub.model.publication :as model.publication]
            [com.platypub.model.request :as model.request]
            [com.platypub.model.send :as model.send]
            [com.platypub.model.send-attempt :as model.send-attempt]
            [com.platypub.model.sqlite :as model.sqlite]
            [com.platypub.model.subscriber :as model.subscriber]
            [com.platypub.model.user :as model.user]
            [com.platypub.work.feed-sync :as work.feed-sync]
            [com.platypub.work.feeds :as work.feeds]
            [com.platypub.work.readiness :as work.readiness]
            [com.platypub.work.readiness-consumer :as work.readiness-consumer]
            [com.platypub.work.resume-sends :as work.resume-sends]
            [com.platypub.work.send :as work.send]
            [com.platypub.work.suppress-bounces :as work.suppress-bounces]))

(defn- on-error
  [{:keys [status], :as ctx}]
  (-> (lib.ui/page
       ctx
       [:h1
        (if (= status 404)
          "Page not found."
          "Something went wrong.")])
      (assoc :status status)))

(def modules
  [{:biff.core/init {:biff.ring/on-error #'on-error}}
   (biff.config/module)
   (biff.ring/module)
   (biff.datastar/module)
   (biff.background/module)
   (biff.fx/module)
   minio/module
   platypub.fx/module
   (biff.graph/module)
   model.content/module
   model.feed/module
   model.global/module
   model.publication/module
   model.request/module
   model.send/module
   model.send-attempt/module
   model.subscriber/module
   model.user/module
   model.sqlite/module
   app.biff-admin/module
   app.landing/module
   app.auth/module
   app.confirm-subscription/module
   app.admin/module
   work.readiness/module
   work.readiness-consumer/module
   work.send/module
   work.feeds/module
   work.feed-sync/module
   work.resume-sends/module
   work.suppress-bounces/module
   app.publication/module
   app.publications/module
   app.publication-settings/module
   app.publication-subscribers/module
   app.publication-send/module
   app.subscription/module
   app.unsubscribe/module
   app.mock-mailersend/module
   api.mock-cdn/module
   api.mock-mailersend/module])

(def start-order
  [:biff.config/module
   :platypub/local-minio
   :biff.sqlite/module
   :biff.admin/module
   :biff.background/module
   :biff.ring/module])
