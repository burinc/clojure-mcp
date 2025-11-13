(ns clojure-mcp.resources-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [clojure-mcp.resources :as resources]
            [clojure.string :as str])
  (:import [java.io File]))

(defn with-temp-dir
  "Create a temporary directory and execute f with it, cleaning up after."
  [f]
  (let [temp-dir (File/createTempFile "test-resources" "")
        dir-path (.getAbsolutePath temp-dir)]
    (.delete temp-dir)
    (.mkdir temp-dir)
    (try
      (f dir-path)
      (finally
        ;; Clean up temp dir and contents
        (doseq [file (.listFiles temp-dir)]
          (.delete file))
        (.delete temp-dir)))))

(defn create-test-file
  "Create a test file with content in the given directory."
  [dir-path filename content]
  (let [file (io/file dir-path filename)]
    (spit file content)
    (.getAbsolutePath file)))

(deftest test-create-resources-from-config
  (testing "Creating resources from configuration"
    (with-temp-dir
      (fn [temp-dir]
        ;; Create test files
        (create-test-file temp-dir "test.md" "# Test Content")
        (create-test-file temp-dir "data.json" "{\"test\": true}")
        (create-test-file temp-dir "code.clj" "(def x 42)")

        (let [config {"test.md" {:description "Test markdown"
                                 :file-path "test.md"}
                      "data.json" {:description "Test JSON"
                                   :file-path "data.json"}
                      "code.clj" {:description "Test Clojure"
                                  :file-path "code.clj"}}
              resources (resources/create-resources-from-config config temp-dir)]

          (testing "All existing files create resources"
            (is (= 3 (count resources))))

          (testing "Resource names match config keys"
            (is (= #{"test.md" "data.json" "code.clj"}
                   (set (map :name resources)))))

          (testing "Descriptions are preserved"
            (let [test-md (first (filter #(= "test.md" (:name %)) resources))]
              (is (= "Test markdown" (:description test-md)))))

          (testing "URLs are auto-generated"
            (let [test-md (first (filter #(= "test.md" (:name %)) resources))]
              (is (= "custom://test-md" (:url test-md)))))

          (testing "Mime types are detected"
            (let [resources-by-name (into {} (map (juxt :name identity) resources))]
              (is (str/includes? (get-in resources-by-name ["test.md" :mime-type]) "markdown"))
              (is (str/includes? (get-in resources-by-name ["data.json" :mime-type]) "json"))
              (is (str/includes? (get-in resources-by-name ["code.clj" :mime-type]) "clojure")))))))))

(deftest test-create-resources-with-missing-files
  (testing "Non-existent files are filtered out"
    (with-temp-dir
      (fn [temp-dir]
        (create-test-file temp-dir "exists.txt" "content")

        (let [config {"exists.txt" {:description "Exists"
                                    :file-path "exists.txt"}
                      "missing.txt" {:description "Missing"
                                     :file-path "missing.txt"}}
              resources (resources/create-resources-from-config config temp-dir)]

          (is (= 1 (count resources)))
          (is (= "exists.txt" (:name (first resources)))))))))

(deftest test-custom-url-and-mime-type
  (testing "Custom URL and mime-type override defaults"
    (with-temp-dir
      (fn [temp-dir]
        (create-test-file temp-dir "custom.txt" "content")

        (let [config {"custom.txt" {:description "Custom resource"
                                    :file-path "custom.txt"
                                    :url "special://my-custom-url"
                                    :mime-type "application/special"}}
              resources (resources/create-resources-from-config config temp-dir)
              resource (first resources)]
          (is (= "special://my-custom-url" (:url resource)))
          (is (= "application/special" (:mime-type resource))))))))

(deftest test-absolute-paths
  (testing "Absolute paths are handled correctly"
    (with-temp-dir
      (fn [temp-dir]
        (let [abs-path (create-test-file temp-dir "absolute.txt" "content")
              config {"absolute.txt" {:description "Absolute path"
                                      :file-path abs-path}}
              resources (resources/create-resources-from-config config "/different/working/dir")]

          (is (= 1 (count resources)))
          (is (= "absolute.txt" (:name (first resources)))))))))

(deftest test-make-resources-with-defaults
  (testing "make-resources includes default resources"
    (with-temp-dir
      (fn [temp-dir]
        ;; Create default resource files
        (create-test-file temp-dir "README.md" "# README")
        (create-test-file temp-dir "PROJECT_SUMMARY.md" "# Summary")

        (let [test-atom (atom {:clojure-mcp.config/config {:nrepl-user-dir temp-dir}})
              resources (resources/make-resources test-atom)
              resource-names (set (map :name resources))]

          ;; Should have at least the files that exist
          (is (contains? resource-names "README.md"))
          (is (contains? resource-names "PROJECT_SUMMARY.md")))))))

(deftest test-make-resources-with-override
  (testing "Config resources override defaults with same name"
    (with-temp-dir
      (fn [temp-dir]
        ;; Create files - both default locations and custom locations
        (create-test-file temp-dir "README.md" "# Original README")
        (create-test-file temp-dir "custom-readme.md" "# Custom README")
        (create-test-file temp-dir "extra.txt" "Extra content")
        ;; Also create other defaults so they appear
        (create-test-file temp-dir "PROJECT_SUMMARY.md" "# Summary")

        (let [config {:resources {"README.md" {:description "Overridden README"
                                               :file-path "custom-readme.md"}
                                  "extra.txt" {:description "Additional resource"
                                               :file-path "extra.txt"}}
                      :nrepl-user-dir temp-dir}
              test-atom (atom {:clojure-mcp.config/config config})
              resources (resources/make-resources test-atom)
              resources-by-name (into {} (map (juxt :name identity) resources))]

          (testing "README is overridden"
            (let [readme (get resources-by-name "README.md")]
              (is (= "Overridden README" (:description readme)))))

          (testing "Additional resource is included"
            (is (contains? resources-by-name "extra.txt"))
            (let [extra (get resources-by-name "extra.txt")]
              (is (= "Additional resource" (:description extra)))))

          (testing "Non-overridden defaults still present"
            (is (contains? resources-by-name "PROJECT_SUMMARY.md"))
            (let [summary (get resources-by-name "PROJECT_SUMMARY.md")]
              (is (= "A Clojure project summary document for the project hosting the REPL, this is intended to provide the LLM with important context to start."
                     (:description summary))))))))))

(deftest test-default-resources-structure
  (testing "Default resources have correct structure"
    (is (map? resources/default-resources))

    (doseq [[name config] resources/default-resources]
      (testing (str "Resource " name)
        (is (string? name))
        (is (contains? config :url))
        (is (contains? config :description))
        (is (contains? config :file-path))
        (is (string? (:url config)))
        (is (string? (:description config)))
        (is (string? (:file-path config)))))))

(deftest test-resource-name-sanitization
  (testing "Resource names with special characters generate valid URLs"
    (with-temp-dir
      (fn [temp-dir]
        (create-test-file temp-dir "test.txt" "content")

        (let [config {"My Resource!@#$.txt" {:description "Special chars"
                                             :file-path "test.txt"}}
              resources (resources/create-resources-from-config config temp-dir)
              resource (first resources)]

          (is (= "custom://my-resource-txt" (:url resource)))
          (is (= "My Resource!@#$.txt" (:name resource))))))))