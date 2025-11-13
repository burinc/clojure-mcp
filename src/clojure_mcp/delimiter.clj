(ns clojure-mcp.delimiter
  "Delimiter error checking using edamame parser."
  (:require [edamame.core :as e]))

(defn delimiter-error?
  "Returns true if the string has a delimiter error specifically.
   Checks both that it's an :edamame/error and has delimiter info.
   Uses :all true to enable all standard Clojure reader features:
   function literals, regex, quotes, syntax-quote, deref, var, etc.
   Also enables :read-cond :allow to support reader conditionals.
   Handles unknown data readers gracefully with a default reader fn."
  [s]
  (try
    (e/parse-string-all s {:all true
                           :features #{:bb :clj :cljs :cljr :default}
                           :read-cond :allow
                           :readers (fn [_tag] (fn [data] data))
                           :auto-resolve name})
    false ; No error = no delimiter error
    (catch clojure.lang.ExceptionInfo ex
      (let [data (ex-data ex)]
        (and (= :edamame/error (:type data))
             (contains? data :edamame/opened-delimiter))))
    (catch Exception _e
      ;; For other exceptions, conservatively return true
      ;; to allow potential delimiter repair attempts
      true)))

(defn count-forms
  "Counts the number of top-level forms in a string using edamame.
   Returns the count of forms, or throws an exception if parsing fails."
  [s]
  (try
    (count (e/parse-string-all s {:all true
                                  :features #{:bb :clj :cljs :cljr :default}
                                  :read-cond :allow
                                  :readers (fn [_tag] (fn [data] data))
                                  :auto-resolve name}))
    (catch Exception e
      (throw (ex-info "Failed to parse forms"
                      {:error (ex-message e)}
                      e)))))
