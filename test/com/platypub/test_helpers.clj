(ns com.platypub.test-helpers)

(defn resolve-resolver
  ([resolver ctx]
   (resolve-resolver resolver ctx {}))
  ([resolver ctx input]
   ((:biff.graph/resolve-fn resolver)
    (assoc ctx :biff.graph/input input))))

(defn resolve-sql
  ([resolver input rows]
   (resolve-sql resolver {} input rows))
  ([resolver ctx input rows]
   ((:biff.graph/resolve-fn resolver)
    (assoc ctx
           :biff.graph/input input
           :biff.fx/handlers
           {:biff.sqlite.fx/execute
            (fn [_ statement]
              (if (fn? rows) (rows statement) rows))}))))
