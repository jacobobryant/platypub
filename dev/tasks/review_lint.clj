(ns tasks.review-lint
  (:require [clojure.edn :as edn]
            [clojure.java.shell :as shell]
            [clojure.string :as str]))

(def module-segments #{"app" "model" "work" "api" "uicomp"})

(defn- segments [ns-name]
  (str/split (str ns-name) #"\."))

(defn- public-function?
  [{:keys [private fixed-arities varargs-min-arity defined-by]}]
  (and (not private)
       (or fixed-arities
           varargs-min-arity
           (#{"com.biffweb.fx/defpipeline" "com.biffweb.fx/defmachine"}
            defined-by))))

(defn lib-usage-violations
  [{:keys [var-definitions var-usages]}]
  (let [counts (into {}
                     (map (fn [[[target-ns _ :as var-id] usages]]
                            [var-id (count (into #{}
                                                 (comp (map :from)
                                                       (remove #{target-ns}))
                                                 usages))]))
                     (group-by (juxt :to :name) var-usages))]
    (for [{:keys [ns name] :as definition} var-definitions
          :when                            (and (some #{"lib"} (segments ns))
                                                (public-function? definition))
          :let                             [n-used (get counts [ns name] 0)]
          :when                            (< n-used 2)]
      (assoc definition
             :kind :lib-usage
             :message
             (str ns "/" name " is used by " n-used
                  " other namespace" (when (not= n-used 1) "s")
                  ". Public lib functions not used by at least two must "
                  "be moved to their call sites.")))))

(defn module-require-violations
  [{:keys [namespace-usages]}]
  (let [usages    (filter #(some module-segments (segments (:to %)))
                          namespace-usages)
        requirers (set (map :from usages))]
    (concat
     (when (> (count requirers) 1)
       [(assoc (first usages)
               :kind :multiple-module-requirers
               :message
               (str "Module namespaces are required by multiple namespaces: "
                    (str/join ", " (sort requirers)) "."))])
     (for [{:keys [from to] :as usage} usages
           :when                       (not= "modules" (last (segments from)))]
       (assoc usage
              :kind :invalid-module-requirer
              :message
              (str to " is required by " from
                   "; module namespaces may only be required by a namespace "
                   "whose last segment is modules."))))))

(defn violations [analysis]
  (sort-by (juxt :filename :row :kind)
           (concat (lib-usage-violations analysis)
                   (module-require-violations analysis))))

(defn- analyze []
  (let [{:keys [exit out err]}
        (shell/sh "clj-kondo" "--lint" "src"
                  "--config" "{:output {:analysis true :format :edn}}")]
    (when-not (zero? exit)
      (throw (ex-info "clj-kondo analysis failed." {:exit exit, :err err})))
    (:analysis (edn/read-string out))))

(defn review-lint
  "Check Biff namespace and lib usage conventions."
  [& _args]
  (let [warnings (violations (analyze))]
    (doseq [{:keys [filename row message]} warnings]
      (println (str filename ":" row ": warning: " message)))
    (println (str (count warnings) " review-lint warning(s)."))
    (when (seq warnings)
      (throw (ex-info "review-lint failed." {:warnings (count warnings)})))))
