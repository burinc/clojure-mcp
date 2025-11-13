(ns clojure-mcp.tools-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure-mcp.tools :as tools]))

(deftest test-build-read-only-tools
  (testing "Read-only tools are created correctly"
    (let [nrepl-client-atom (atom {})
          read-only-tools (tools/build-read-only-tools nrepl-client-atom)]
      (is (vector? read-only-tools))
      (is (pos? (count read-only-tools)))
      ;; Check that all tools have required keys for MCP registration
      (doseq [tool read-only-tools]
        (is (map? tool))
        ;; Tools should have either :tool-type (for multimethod tools) or :name/:id (for registration maps)
        (is (or (contains? tool :tool-type)
                (and (contains? tool :name)
                     (contains? tool :id))))))))

(deftest test-build-all-tools
  (testing "All tools are created correctly"
    (let [nrepl-client-atom (atom {})
          all-tools (tools/build-all-tools nrepl-client-atom)]
      (is (vector? all-tools))
      (is (pos? (count all-tools)))
      ;; All tools should be a superset of read-only tools
      (let [read-only-count (count (tools/build-read-only-tools nrepl-client-atom))]
        (is (>= (count all-tools) read-only-count))))))
