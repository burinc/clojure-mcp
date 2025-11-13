(ns clojure-mcp.utils.file
  "UTF-8 file I/O utilities.
   
   This namespace provides UTF-8-aware versions of standard file I/O functions
   to ensure consistent encoding across all platforms, especially Windows where
   the JVM default encoding is Windows-1252 instead of UTF-8."
  (:refer-clojure :exclude [spit slurp]))

(defn slurp-utf8 [f]
  (clojure.core/slurp f :encoding "UTF-8"))

(defn spit-utf8 [f content & options]
  (apply clojure.core/spit f content :encoding "UTF-8" options))
