(ns clojure-mcp.tools.nrepl-ports.tool
  "MCP tool implementation for listing nREPL ports.
   Discovers running nREPL servers and provides detailed information about each."
  (:require
   [clojure-mcp.tool-system :as tool-system]
   [clojure-mcp.tools.nrepl-ports.core :as core]
   [clojure-mcp.config :as config]
   [clojure.string :as string]))

;; Factory function to create the tool configuration
(defn create-list-nrepl-ports-tool
  "Creates the list-nrepl-ports tool configuration.

   Parameters:
   - nrepl-client-atom: Atom containing the nREPL client"
  [nrepl-client-atom]
  {:tool-type :list-nrepl-ports
   :nrepl-client-atom nrepl-client-atom})

;; Implement the required multimethods for the list-nrepl-ports tool
(defmethod tool-system/tool-name :list-nrepl-ports [_]
  "list_nrepl_ports")

(defmethod tool-system/tool-description :list-nrepl-ports [_]
  "Discover and list all running nREPL servers on this machine.
 - Checks for .nrepl-port files in the current directory
 - Scans for Java/Clojure/Babashka processes listening on TCP ports
 - Validates each port responds to nREPL protocol
 - Returns detailed info including environment type (clj, bb, shadow, etc.) and project directory
 - Useful for finding available REPLs to connect to")

(defmethod tool-system/tool-schema :list-nrepl-ports [_]
  {:type :object
   :properties {:explanation {:type :string
                              :description "Brief explanation of why you need to list nREPL ports"}}
   :required [:explanation]})

(defmethod tool-system/validate-inputs :list-nrepl-ports [{:keys [nrepl-client-atom]} _inputs]
  (let [nrepl-client-map @nrepl-client-atom
        current-dir (config/get-nrepl-user-dir nrepl-client-map)]
    {:current-dir (or current-dir (System/getProperty "user.dir"))}))

(defmethod tool-system/execute-tool :list-nrepl-ports [_ inputs]
  (let [{:keys [current-dir]} inputs]
    (core/discover-nrepl-ports current-dir)))

(defn format-env-type
  "Format environment type for display."
  [env-type]
  (case env-type
    :clj "Clojure"
    :bb "Babashka"
    :basilisp "Basilisp"
    :scittle "Scittle"
    :shadow "Shadow-CLJS"
    :unknown "Unknown"
    "N/A"))

(defn format-port-info
  "Format a single port info map for display."
  [port-info]
  (let [{:keys [host port source valid env-type project-dir matches-cwd session-count]} port-info]
    (if valid
      (str "  " host ":" port " (" (format-env-type env-type) ")"
           (when matches-cwd " [current project]")
           "\n    Source: " (name source)
           "\n    Sessions: " session-count
           (when project-dir (str "\n    Project: " project-dir)))
      (str "  " host ":" port " (not responding)"
           "\n    Source: " (name source)))))

(defmethod tool-system/format-results :list-nrepl-ports [_ result]
  (if (empty? result)
    {:result ["No nREPL servers found.\n\nTo start a Clojure REPL with nREPL, run:\n  clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version \"1.3.0\"}}}' -M -m nrepl.cmdline"]
     :error false}
    (let [valid-ports (filter :valid result)
          invalid-ports (filter (complement :valid) result)
          current-project-ports (filter :matches-cwd valid-ports)
          other-project-ports (filter (complement :matches-cwd) valid-ports)

          output (str "Found " (count valid-ports) " active nREPL server(s)"
                      (when (seq invalid-ports)
                        (str " (" (count invalid-ports) " not responding)"))
                      "\n\n"
                      (when (seq current-project-ports)
                        (str "Current project:\n"
                             (string/join "\n\n" (map format-port-info current-project-ports))
                             "\n\n"))
                      (when (seq other-project-ports)
                        (str "Other projects:\n"
                             (string/join "\n\n" (map format-port-info other-project-ports))
                             "\n")))]
      {:result [output]
       :error false})))

;; Backward compatibility function that returns the registration map
(defn list-nrepl-ports-tool
  "Returns the registration map for the list-nrepl-ports tool.

   Parameters:
   - nrepl-client-atom: Atom containing the nREPL client"
  [nrepl-client-atom]
  (tool-system/registration-map (create-list-nrepl-ports-tool nrepl-client-atom)))
