(ns com.platypub.app.mock-mailersend
  (:require [com.biffweb.fx :refer [defpipeline]]
            [com.biffweb.ring :refer [defpath]]
            [com.platypub.lib.ui :as ui]))

(defpath inbox-path "/_mock/mailersend")

(defpipeline inbox
  (fn [{:keys [platypub/mock-mailersend-enabled
               platypub/mock-mailersend-state]}]
    (if mock-mailersend-enabled
      {:state [:platypub/deref mock-mailersend-state]}
      {:biff.fx/return {:status 404}}))

  (fn [request {:keys [state]}]
    (ui/page
     request
     [:main
      {:class ["mx-auto max-w-3xl p-8"]}
      [:h1 {:class ["mb-6 text-2xl font-bold"]} "Mock MailerSend"]
      (for [{:keys [to subject html]} (reverse (:emails state))]
        [:article
         {:class ["mb-4 rounded border p-4"]}
         [:div {:class ["font-semibold"]} subject]
         [:div (str "To: " (get-in to [0 :email]))]
         [:details [:summary "HTML"] [:div html]]])])))

(def module
  {:biff.ring/routes [[(inbox-path) {:get inbox}]]})
