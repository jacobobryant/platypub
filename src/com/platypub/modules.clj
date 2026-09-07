(ns com.platypub.modules
  (:require [com.biffweb.background :as biff.background]
            [com.biffweb.config :as biff.config]
            [com.biffweb.datastar :as biff.datastar]
            [com.biffweb.fx :as biff.fx]
            [com.biffweb.graph :as biff.graph]
            [com.biffweb.ring :as biff.ring]
            [com.platypub.app.admin :as app.admin]
            [com.platypub.app.auth :as app.auth]
            [com.platypub.app.demo :as app.demo]
            [com.platypub.app.landing :as app.landing]
            [com.platypub.lib.ui :as lib.ui]
            [com.platypub.model.sqlite :as model.sqlite]
            [com.platypub.model.tab :as model.tab]
            [com.platypub.model.user :as model.user]))

(def modules
  [{:biff.core/init {:biff.ring/on-error #'lib.ui/on-error}}
   (biff.config/module)
   (biff.ring/module)
   (biff.datastar/module)
   (biff.background/module)
   (biff.fx/module)
   (biff.graph/module)
   model.user/module
   model.sqlite/module
   model.tab/module
   app.admin/module
   app.landing/module
   app.auth/module
   app.demo/module])

(def start-order
  [:biff.config/module
   :biff.sqlite/module
   :biff.admin/module
   :biff.background/module
   :biff.ring/module])
