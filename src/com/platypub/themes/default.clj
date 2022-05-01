(ns com.platypub.themes.default)

(defn site [sys {:keys [site posts]}]
  {"/index.html" "this is aweshome"})

(def theme
  {:site site})
