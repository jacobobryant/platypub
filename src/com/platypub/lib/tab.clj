(ns com.platypub.lib.tab)

(defn write-statement
  [tab-id current updates]
  (let [data (reduce-kv (fn [state attribute value]
                          (if (nil? value)
                            (dissoc state attribute)
                            (assoc state attribute value)))
                        current
                        updates)]
    {:insert-into   :tab-state
     :values        [{:tab-state/id tab-id, :tab-state/data [:lift data]}]
     :on-conflict   [:tab-state/id]
     :do-update-set [:tab-state/data]}))
