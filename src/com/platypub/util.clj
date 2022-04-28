(ns com.platypub.util)

(defn split-by [pred coll]
  [(remove pred coll)
   (filter pred coll)])
