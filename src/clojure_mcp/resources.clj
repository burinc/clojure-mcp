(ns clojure-mcp.resources
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure-mcp.config :as config]
            [clojure-mcp.file-content :as file-content]
            [clojure-mcp.tools.project.core :as project]
            [clojure-mcp.utils.file :as file-utils]))

(defn read-file [full-path]
  (let [file (io/file full-path)]
    (if (.exists file)
      (try
        (file-utils/slurp-utf8 file)
        (catch Exception e
          (throw (ex-info (str "reading file- " full-path
                               "\nException- " (.getMessage e))
                          {:path full-path}
                          e))))
      (throw (ex-info (str "File not found- " full-path
                           "\nAbsolute path- " (.getAbsolutePath file))
                      {:path full-path})))))

(defn create-file-resource
  "Creates a resource specification for serving a file.
   Takes a full file path resolved with the correct working directory."
  [url name description mime-type full-path]
  {:url url
   :name name
   :description description
   :mime-type mime-type
   :resource-fn
   (fn [_ _ clj-result-k]
     (try
       (let [result (read-file full-path)]
         (clj-result-k [result]))
       (catch Exception e
         (clj-result-k [(str "Error in resource function: "
                             (ex-message e)
                             "\nFor file: " full-path)]))))})

(defn create-string-resource
  "Creates a resource specification for serving a string.
   Accepts nrepl-client-atom for consistency with create-file-resource, but doesn't use it.

   :contents should be a vector of strings"
  [url name description mime-type contents & [nrepl-client-atom]]
  {:url url
   :name name
   :description description
   :mime-type mime-type
   :resource-fn (fn [_ _ clj-result-k]
                  (clj-result-k contents))})

(defn create-resources-from-config
  "Creates resources from configuration map.
   Takes a config map where keys are resource names and values contain
   :description, :file-path, and optionally :url and :mime-type.
   Returns a seq of resource maps."
  [resources-config working-dir]
  (keep
   (fn [[resource-name {:keys [description file-path url mime-type]}]]
     (let [full-path (if (.isAbsolute (io/file file-path))
                       file-path
                       (.getCanonicalPath (io/file working-dir file-path)))
           file (io/file full-path)]
       (when (.exists file)
         (let [actual-mime-type (or mime-type
                                    (file-content/mime-type full-path)
                                    "text/plain")
               actual-url (or url
                              (str "custom://"
                                   (str/lower-case
                                    (str/replace resource-name #"[^a-zA-Z0-9]+" "-"))))]
           (create-file-resource
            actual-url
            resource-name
            description
            actual-mime-type
            full-path)))))
   resources-config))

(def default-resources
  "Map of default resources keyed by name.
   Each resource has :url, :description, and :file-path.
   The mime-type will be auto-detected when creating the actual resource."
  {"PROJECT_SUMMARY.md" {:url "custom://project-summary"
                         :description "A Clojure project summary document for the project hosting the REPL, this is intended to provide the LLM with important context to start."
                         :file-path "PROJECT_SUMMARY.md"}

   "README.md" {:url "custom://readme"
                :description "A README document for the current Clojure project hosting the REPL"
                :file-path "README.md"}

   "CLAUDE.md" {:url "custom://claude"
                :description "The Claude instructions document for the current project hosting the REPL"
                :file-path "CLAUDE.md"}

   "LLM_CODE_STYLE.md" {:url "custom://llm-code-style"
                        :description "Guidelines for writing Clojure code for the current project hosting the REPL"
                        :file-path "LLM_CODE_STYLE.md"}})

(defn make-resources
  "Creates all resources for the MCP server, combining defaults with configuration.
   Config resources can override defaults by using the same name."
  [nrepl-client-atom]
  (let [;; Get working directory from the nrepl-client-atom
        working-dir (config/get-nrepl-user-dir @nrepl-client-atom)

        ;; Start with default resources
        default-resources-list (create-resources-from-config
                                default-resources
                                working-dir)

        ;; Get configured resources from config
        config-resources (config/get-resources @nrepl-client-atom)
        config-resources-list (when config-resources
                                (create-resources-from-config
                                 config-resources
                                 working-dir))

        ;; Merge resources, with config overriding defaults by name
        all-resources (if config-resources-list
                        (let [config-names (set (map :name config-resources-list))
                              filtered-defaults (remove #(contains? config-names (:name %))
                                                        default-resources-list)]
                          (concat filtered-defaults config-resources-list))
                        default-resources-list)

        ;; Add dynamic project-info resource
        project-info-resource (let [{:keys [outputs error]} (project/inspect-project nrepl-client-atom)]
                                (when-not error
                                  (create-string-resource
                                   "custom://project-info"
                                   "Clojure Project Info"
                                   "Information about the current Clojure project structure, attached REPL environment and dependencies"
                                   "text/markdown"
                                   outputs)))]
    (keep identity (conj (vec all-resources) project-info-resource))))
