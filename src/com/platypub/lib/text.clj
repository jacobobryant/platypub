(ns com.platypub.lib.text
  (:require [clojure.string :as str]))

(defn html->text [html]
  (-> (or html "")
      (str/replace #"(?is)<(script|style).*?>.*?</\1>" "")
      (str/replace #"(?i)<br\s*/?>" "\n")
      (str/replace #"(?i)</p>" "\n\n")
      (str/replace #"(?s)<[^>]+>" "")
      (str/replace "&nbsp;" " ")
      (str/replace "&amp;" "&")
      (str/replace "&lt;" "<")
      (str/replace "&gt;" ">")
      str/trim))
