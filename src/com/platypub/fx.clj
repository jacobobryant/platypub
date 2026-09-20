(ns com.platypub.fx
  (:require [clojure.java.io :as io])
  (:import [java.io InputStream]
           [java.nio.charset StandardCharsets]
           [java.nio.file Path]))

(defn reset-atom
  [_ctx state value]
  (reset! state value))

(defn swap-atom
  [_ctx state f & args]
  (apply swap! state f args))

(defn deref-atom
  [_ctx state]
  @state)

(defn sleep
  [_ctx milliseconds]
  (Thread/sleep milliseconds))

(defn read-uploaded-file
  [_ctx value]
  (cond
    (instance? InputStream value)
    (with-open [reader (io/reader value)]
      (slurp reader))

    (instance? java.io.File value)
    (slurp value)

    (instance? Path value)
    (slurp value)

    (instance? (Class/forName "[B") value)
    (String. ^bytes value StandardCharsets/UTF_8)

    :else
    (throw (ex-info "Unsupported uploaded file value."
                    {:class (some-> value class str)}))))

(def module
  {:biff.fx/handlers
   {:biff.fx/sleep               sleep
    :platypub/deref              deref-atom
    :platypub/read-uploaded-file read-uploaded-file
    :platypub/reset-atom         reset-atom
    :platypub/swap-atom          swap-atom}})
