(ns com.platypub.repl
  (:require [com.biffweb :as biff :refer [q]]
            [com.platypub.netlify :as netlify]
            [clj-http.client :as http]
            [xtdb.api :as xt]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(defn migrate! [email]
  (let [{:keys [biff/db biff.xtdb/node]} (get-sys)
        user-id (biff/lookup-id db :user/email email)
        tx (for [[lookup-key user-key] [[:post/title :post/user]
                                        [:image/url :image/user]
                                        [:site/url :site/user]
                                        [:list/title :list/user]]
                 [doc] (xt/q db
                             {:find '[(pull doc [*])]
                              :where [['doc lookup-key]]})]
             [::xt/put (-> doc
                           (assoc user-key user-id)
                           (dissoc :post/authors))])]
    (xt/submit-tx node tx)))

(comment

  ;; If I eval (biff/refresh) with Conjure, it starts sending stdout to Vim.
  ;; fix-print makes sure stdout keeps going to the terminal.
  (biff/fix-print (biff/refresh))


  (sort (keys (:body result)))
  (:url (:body result)) ; nil

  (let [{:keys [biff/db netlify/api-key] :as sys} (get-sys)]
    )

  (sort (keys @biff/system))

  )
