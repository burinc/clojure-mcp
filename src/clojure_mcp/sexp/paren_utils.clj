(ns clojure-mcp.sexp.paren-utils
  (:require
   [clojure-mcp.delimiter :as delimiter])
  (:import [com.oakmac.parinfer Parinfer]))

(defn parinfer-repair [code-str]
  (let [res (Parinfer/indentMode code-str nil nil nil false)]
    (when (and (.success res)
               (not (delimiter/delimiter-error? (.text res))))
      (.text res))))