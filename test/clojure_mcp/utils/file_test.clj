(ns clojure-mcp.utils.file-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure-mcp.utils.file :as file])
  (:import [java.io File]))

(deftest slurp-utf8-test
  (testing "Reading UTF-8 content"
    (let [temp-file (File/createTempFile "test-slurp" ".txt")]
      (try
        (clojure.core/spit temp-file "conexão\nParâmetros\ndescriçã" :encoding "UTF-8")
        (is (= "conexão\nParâmetros\ndescriçã" (file/slurp-utf8 temp-file)))
        (finally
          (.delete temp-file)))))

  (testing "Reading from File object"
    (let [temp-file (File/createTempFile "test-file-obj" ".txt")]
      (try
        (clojure.core/spit temp-file "test content" :encoding "UTF-8")
        (is (= "test content" (file/slurp-utf8 temp-file)))
        (finally
          (.delete temp-file)))))

  (testing "Reading from file path string"
    (let [temp-file (File/createTempFile "test-path" ".txt")
          path (.getAbsolutePath temp-file)]
      (try
        (clojure.core/spit path "path test" :encoding "UTF-8")
        (is (= "path test" (file/slurp-utf8 path)))
        (finally
          (.delete temp-file))))))

(deftest spit-utf8-test
  (testing "Writing UTF-8 content"
    (let [temp-file (File/createTempFile "test-spit" ".txt")
          content "conexão\nParâmetros\ndescriçã"]
      (try
        (file/spit-utf8 temp-file content)
        (is (= content (clojure.core/slurp temp-file :encoding "UTF-8")))
        (finally
          (.delete temp-file)))))

  (testing "Writing with append option"
    (let [temp-file (File/createTempFile "test-append" ".txt")]
      (try
        (file/spit-utf8 temp-file "first\n")
        (file/spit-utf8 temp-file "second\n" :append true)
        (is (= "first\nsecond\n" (file/slurp-utf8 temp-file)))
        (finally
          (.delete temp-file)))))

  (testing "Writing to file path string"
    (let [temp-file (File/createTempFile "test-spit-path" ".txt")
          path (.getAbsolutePath temp-file)]
      (try
        (file/spit-utf8 path "path content")
        (is (= "path content" (file/slurp-utf8 path)))
        (finally
          (.delete temp-file))))))

(deftest round-trip-test
  (testing "Round-trip UTF-8 content preservation"
    (let [temp-file (File/createTempFile "test-round-trip" ".txt")
          test-strings ["conexão com banco de dados"
                        "Parâmetros: configuração"
                        "描述 (Chinese characters)"
                        "Описание (Cyrillic)"
                        "🔥 emoji test 🚀"
                        "Mixed: café, naïve, résumé"]]
      (try
        (doseq [test-str test-strings]
          (file/spit-utf8 temp-file test-str)
          (is (= test-str (file/slurp-utf8 temp-file))
              (str "Failed round-trip for: " test-str)))
        (finally
          (.delete temp-file))))))
