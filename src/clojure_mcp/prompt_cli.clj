(ns clojure-mcp.prompt-cli
  "Command-line interface for interacting with the parent agent"
  (:require [clojure.tools.cli :as cli]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.pprint :as pp]
            [clojure.data.json :as json]
            [clojure-mcp.nrepl :as nrepl]
            [clojure-mcp.config :as config]
            [clojure-mcp.dialects :as dialects]
            [clojure-mcp.core :as core]
            [clojure-mcp.tools.agent-tool-builder.core :as agent-core]
            [clojure-mcp.tools.agent-tool-builder.default-agents :as default-agents]
            [clojure-mcp.agent.general-agent :as general-agent]
            [clojure-mcp.agent.langchain.chat-listener :as listener]
            [clojure-mcp.tool-format :as tool-format])
  (:import [dev.langchain4j.data.message ChatMessageSerializer ChatMessageDeserializer]
           [java.time LocalDateTime]
           [java.time.format DateTimeFormatter])
  (:gen-class))

(def cli-options
  [["-p" "--prompt PROMPT" "Prompt to send to the agent"
    :missing "Prompt is required"]
   ["-r" "--resume" "Resume latest session" :default false]
   ["-m" "--model MODEL" "Model to use (e.g., :openai/gpt-4, :anthropic/claude-3-5-sonnet)"
    :parse-fn (fn [s]
                (if (str/starts-with? s ":")
                  (keyword (subs s 1))
                  (keyword s)))]
   ["-c" "--config CONFIG" "Path to agent configuration file (optional)"]
   ["-d" "--dir DIRECTORY" "Working directory (defaults to REPL's working directory)"
    :validate [#(.isDirectory (io/file %)) "Must be a valid directory"]]
   ["-P" "--port PORT" "nREPL server port"
    :default 7888
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 65536) "Must be a valid port number"]]
   ["-h" "--help" "Show help"]])

(defn load-agent-config
  "Load agent configuration from a file path or resource"
  [config-path]
  (let [config-source (or (io/resource config-path)
                          (when (.exists (io/file config-path))
                            (io/file config-path)))]
    (when-not config-source
      (throw (ex-info "Agent configuration not found"
                      {:config-path config-path})))
    (-> config-source
        slurp
        edn/read-string)))

(defn load-system-message
  "Load system message from resource if it's a path, otherwise return as-is"
  [system-message]
  (if (and (string? system-message)
           (or (str/ends-with? system-message ".md")
               (str/ends-with? system-message ".txt")))
    (if-let [resource (io/resource system-message)]
      (slurp resource)
      system-message)
    system-message))

(defn sessions-dir
  "Get the sessions directory path for a given working directory"
  [working-dir]
  (io/file working-dir ".clojure-mcp" "prompt-cli-sessions"))

(defn ensure-sessions-dir!
  "Ensure the sessions directory exists"
  [working-dir]
  (let [dir (sessions-dir working-dir)]
    (.mkdirs dir)
    dir))

(defn generate-session-filename
  "Generate a timestamp-based filename for a session"
  []
  (let [formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH-mm-ss")
        timestamp (.format (LocalDateTime/now) formatter)]
    (str timestamp ".json")))

(defn save-session!
  "Save messages to a session file with model metadata"
  [messages model-str working-dir filename]
  (let [sessions-dir (ensure-sessions-dir! working-dir)
        session-file (io/file sessions-dir filename)
        messages-json (ChatMessageSerializer/messagesToJson messages)
        formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss")
        created (.format (LocalDateTime/now) formatter)
        session-data {:model model-str
                      :created created
                      :messages messages-json}
        json-str (json/write-str session-data :escape-slash false)]
    (spit session-file json-str)
    session-file))

(defn find-latest-session-file
  "Find the most recent session file in the sessions directory.
   Returns the File object or nil if no sessions exist."
  [working-dir]
  (let [sessions-dir (sessions-dir working-dir)]
    (when (.exists sessions-dir)
      (let [files (->> (.listFiles sessions-dir)
                       (filter #(.isFile %))
                       (filter #(str/ends-with? (.getName %) ".json"))
                       (sort-by #(.getName %) #(compare %2 %1)))] ; Reverse sort by name (timestamp)
        (first files)))))

(defn load-session
  "Load a session from a file.
   Returns {:model \"...\" :messages [...]} or throws if file doesn't exist or is invalid."
  [session-file]
  (let [json-str (slurp session-file)
        session-data (json/read-str json-str :key-fn keyword)
        messages-json (:messages session-data)
        messages (ChatMessageDeserializer/messagesFromJson messages-json)]
    {:model (:model session-data)
     :messages messages
     :filename (.getName session-file)}))

(defn extract-tool-executions
  "Extract tool request/result pairs from messages.
   Returns vector of {:type :tool-execution :request <req> :result <res>}"
  [messages]
  (when (and (seq messages)
             (= "TOOL_EXECUTION_RESULT" (:type (last messages))))
    (let [reversed (reverse messages)
          results (take-while #(= "TOOL_EXECUTION_RESULT" (:type %)) reversed)
          ai-msg (first (drop-while #(= "TOOL_EXECUTION_RESULT" (:type %)) reversed))]
      (when (and ai-msg (= "AI" (:type ai-msg)))
        (let [requests (:toolExecutionRequests ai-msg)]
          (mapv (fn [req res]
                  {:type :tool-execution
                   :request req
                   :result res})
                requests
                (reverse results)))))))

(defn print-tool-executions
  "Print formatted tool executions"
  [executions]
  (when (> (count executions) 1)
    (println "Tool calls count:" (count executions)))
  (doseq [{:keys [request result]} executions]
    (println (tool-format/format-tool-request request))
    (println (tool-format/format-tool-result result))
    (println)))

(defn create-message-capturing-listener
  "Create a listener that captures all messages in an atom.
   Returns [listener messages-atom] tuple."
  []
  (let [messages-atom (atom [])]
    [(listener/create-listener
      {:on-request (fn [req]
                     (when-let [msgs (:messages req)]
                       (reset! messages-atom (vec msgs))))
       :on-response (fn [resp]
                      (when-let [ai-msg (:ai-message resp)]
                        (swap! messages-atom conj ai-msg)))})
     messages-atom]))

(defn create-pretty-print-listener
  "Create a listener that pretty-prints the last message in requests and responses"
  []
  (listener/create-listener
   {:on-request (fn [req]
                  (when-let [messages (:messages req)]
                    (when-let [executions (extract-tool-executions messages)]
                      (print-tool-executions executions))))
    :on-response (fn [resp]
                   (when-let [ai-message (:ai-message resp)]
                     (when-let [text (:text ai-message)]
                       (println "\n=== Response ===")
                       (println text)
                       (println))))
    :on-error (fn [err]
                (println "\n=== Error ===")
                (println (get-in err [:error :message]))
                (println (get-in err [:error :class]))
                (println (get-in err [:error :stack-trace])))}))

(defn run-prompt
  "Execute a prompt against the parent agent"
  [{:keys [prompt model config dir port resume]}]
  (try
    ;; Connect to nREPL and initialize with configuration
    (println (str "Connecting to nREPL server on port " port "..."))
    (let [nrepl-client-map (nrepl/create {:port port})
          _ (nrepl/start-polling nrepl-client-map)

          ;; Detect environment type
          env-type (dialects/detect-nrepl-env-type nrepl-client-map)

          ;; Fetch project directory from REPL or use CLI option
          project-dir (or dir
                          (dialects/fetch-project-directory nrepl-client-map env-type nil))

          ;; Load configuration  
          _ (println (str "Working directory: " project-dir))
          config-data (config/load-config nil project-dir)
          final-env-type (or (:nrepl-env-type config-data) env-type)

          ;; Attach config to nrepl-client-map
          nrepl-client-map-with-config (assoc nrepl-client-map
                                              ::config/config
                                              (assoc config-data :nrepl-env-type final-env-type))

          ;; Initialize environment
          _ (dialects/initialize-environment nrepl-client-map-with-config final-env-type)
          _ (dialects/load-repl-helpers nrepl-client-map-with-config final-env-type)

          nrepl-client-atom (atom nrepl-client-map-with-config)

          ;; Handle resume logic
          resume-data (when resume
                        (if-let [session-file (find-latest-session-file project-dir)]
                          (do
                            (println (str "Resuming session from: " (.getName session-file)))
                            (load-session session-file))
                          (throw (ex-info "No previous sessions found. Cannot resume."
                                          {:resume true :sessions-dir (sessions-dir project-dir)}))))

          session-filename (if resume
                             (:filename resume-data)
                             (generate-session-filename))

          ;; Determine model to use
          model-to-use (or model
                           (:model resume-data)
                           (when-not config
                             (:model (default-agents/parent-agent-config))))

          ;; Load agent configuration - use parent-agent-config by default
          agent-config (if config
                         (load-agent-config config)
                         (default-agents/parent-agent-config))

          ;; Override model if determined
          agent-config (if model-to-use
                         (assoc agent-config :model model-to-use)
                         agent-config)

          ;; Load system message from resource if needed (only for file-based configs)
          agent-config (if (and config (string? (:system-message agent-config)))
                         (update agent-config :system-message load-system-message)
                         agent-config)

          ;; Create message-capturing listener
          [msg-listener messages-atom] (create-message-capturing-listener)

          ;; Add listeners to agent config
          pp-listener (create-pretty-print-listener)
          agent-config (assoc agent-config :listeners [pp-listener msg-listener])

          ;; Build the agent
          _ (println (str "Building agent with model: "
                          (or model-to-use (:model agent-config))))
          agent (agent-core/build-agent-from-config nrepl-client-atom agent-config)

          ;; If resuming, add loaded messages to agent's memory
          _ (when resume
              (when-let [loaded-messages (:messages resume-data)]
                (println (str "Loading " (count loaded-messages) " messages from session..."))
                (doseq [msg loaded-messages]
                  (.add (:memory agent) msg))))

          ;; Send the prompt and get response
          _ (println "\nProcessing prompt...")
          response (general-agent/chat-with-agent agent prompt)

          ;; Save session after response
          model-str (str (or model-to-use (:model agent-config)))
          _ (when-let [msgs @messages-atom]
              (when (seq msgs)
                (save-session! msgs model-str project-dir session-filename)
                (println (str "\nSession saved to: " session-filename))))]

      (if (:error response)
        (do
          (println "\nError occurred:")
          (println (:result response))
          (System/exit 1))
        (do
          (println "\n" (:result response))
          (System/exit 0))))

    (catch Exception e
      (println "\nFailed to execute prompt:")
      (println (.getMessage e))
      (clojure.pprint/pprint (ex-data e))
      (when (System/getenv "DEBUG")
        (.printStackTrace e))
      (System/exit 1))))

(defn -main
  "Main entry point for the CLI"
  [& args]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts args cli-options)]

    (cond
      ;; Show help
      (:help options)
      (do
        (println "Clojure MCP Prompt CLI")
        (println "\nUsage: clojure -M:prompt-cli [options]")
        (println "\nOptions:")
        (println summary)
        (println "\nExamples:")
        (println "  clojure -M:prompt-cli -p \"What namespaces are available?\"")
        (println "  clojure -M:prompt-cli -p \"Evaluate (+ 1 2)\" -m :openai/gpt-4")
        (println "  clojure -M:prompt-cli -p \"Create a fibonacci function\"")
        (println "  clojure -M:prompt-cli -p \"Run my prompt\" -c custom-agent.edn")
        (println "  clojure -M:prompt-cli -p \"Analyze project\" -P 8888  # Custom port")
        (println "  clojure -M:prompt-cli --resume -p \"Continue previous task\"  # Resume latest session")
        (println "  clojure -M:prompt-cli --resume -p \"Next step\" -m :openai/gpt-4  # Resume with different model")
        (System/exit 0))

      ;; Validation errors
      errors
      (do
        (println "Error parsing arguments:")
        (doseq [error errors]
          (println error))
        (System/exit 1))

      ;; Missing prompt
      (not (:prompt options))
      (do
        (println "Error: Prompt is required")
        (println "Use -h or --help for usage information")
        (System/exit 1))

      ;; Execute the prompt
      :else
      (run-prompt options))))
