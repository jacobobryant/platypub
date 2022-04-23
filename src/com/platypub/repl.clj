(ns com.platypub.repl
  (:require [com.biffweb :as biff :refer [q]]
            [clj-http.client :as http]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(comment

  ;; If I eval (biff/refresh) with Conjure, it starts sending stdout to Vim.
  ;; fix-print makes sure stdout keeps going to the terminal.
  (biff/fix-print (biff/refresh))


  (sort (keys (:body result)))
  (:url (:body result)) ; nil

  (let [{:keys [biff/db netlify/api-key] :as sys} (get-sys)]
    (def result (http/post "https://api.netlify.com/api/v1/sites"
                           {:oauth-token api-key
                            :as :json}))

    
    )

  (sort (keys @biff/system))

  )
