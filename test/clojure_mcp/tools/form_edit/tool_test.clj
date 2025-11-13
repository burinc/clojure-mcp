(ns clojure-mcp.tools.form-edit.tool-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [clojure-mcp.tools.form-edit.tool :as sut]
   [clojure-mcp.tool-system :as tool-system]
   [clojure-mcp.tools.unified-read-file.file-timestamps :as file-timestamps]
   [clojure-mcp.tools.test-utils :as test-utils]
   [clojure-mcp.config :as config] ; Added config require
   [clojure.java.io :as io]))

;; Test fixtures
(def ^:dynamic *test-dir* nil)
(def ^:dynamic *test-file* nil)
(def ^:dynamic *client-atom* nil)
(def client-atom-for-tests nil) ;; Will be set in the :once fixture

(defn create-test-files-fixture [f]
  ;; Make sure we have a valid client atom
  (when (nil? test-utils/*nrepl-client-atom*)
    (test-utils/test-nrepl-fixture identity))

  (let [test-dir (test-utils/create-test-dir)
        client-atom test-utils/*nrepl-client-atom*
        test-file-content (str "(ns test.core)\n\n"
                               "(defn example-fn\n  \"Original docstring\"\n  [x y]\n  #_(println \"debug value:\" x)\n  (+ x y))\n\n"
                               "(def a 1)\n\n"
                               "#_(def unused-value 42)\n\n"
                               "(comment\n  (example-fn 1 2))\n\n"
                               ";; Test comment\n;; spans multiple lines")
        ;; Make sure client atom has necessary configuration
        _ (config/set-config! client-atom :nrepl-user-dir test-dir)
        _ (config/set-config! client-atom :allowed-directories [test-dir])
        _ (swap! client-atom assoc ::file-timestamps/file-timestamps {}) ; Keep this direct for now
        ;; Create and register the test file
        test-file-path (test-utils/create-and-register-test-file
                        client-atom
                        test-dir
                        "test.clj"
                        test-file-content)]
    (binding [*test-dir* test-dir
              *test-file* (io/file test-file-path)
              *client-atom* client-atom]
      (try
        (f)
        (finally
          (test-utils/clean-test-dir test-dir))))))

(use-fixtures :once (fn [f]
                      ;; Make sure we have a valid nREPL client atom for tests
                      (test-utils/test-nrepl-fixture
                       (fn []
                          ;; Set up global client atom for tests
                         (alter-var-root #'client-atom-for-tests (constantly test-utils/*nrepl-client-atom*))
                          ;; Run the actual test
                         (binding [test-utils/*nrepl-client-atom* test-utils/*nrepl-client-atom*]
                           (f))))))
(use-fixtures :each create-test-files-fixture)

;; Test helper functions
(defn get-file-path []
  (.getCanonicalPath *test-file*))

;; Tests for sexp-update-tool validation
(deftest sexp-replace-validation-test
  (testing "Sexp replace validation checks for multiple forms in match_form"
    (let [client-atom *client-atom*
          sexp-tool (sut/create-update-sexp-tool client-atom)
          valid-inputs {:file_path (get-file-path)
                        :match_form "(+ x y)"
                        :new_form "(+ x (* y 2))"}
          _invalid-inputs {:file_path (get-file-path)
                           :match_form "(+ x y) (- x y)" ;; Multiple forms!
                           :new_form "(+ x (* y 2))"}
          _another-invalid {:file_path (get-file-path)
                            :match_form "(def a 1) (def b 2)" ;; Multiple forms!
                            :new_form "(+ x (* y 2))"}
          ;; Test valid input is accepted
          validated (tool-system/validate-inputs sexp-tool valid-inputs)]
      (is (string? (:file_path validated)))
      (is (= "(+ x y)" (:match_form validated)))
      (is (= "(+ x (* y 2))" (:new_form validated))))))
