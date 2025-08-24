(ns clojure-mcp.tools.shadow-cljs.helper
  "Helper functions for connecting to and evaluating ClojureScript 
   in shadow-cljs projects via nREPL."
  (:require [nrepl.core :as nrepl]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

;; =============================================================================
;; Connection Management
;; =============================================================================

(defonce connections (atom {}))

(defn connect
  "Connect to a shadow-cljs nREPL server.
   Returns a connection map with :conn and :client keys."
  ([port]
   (connect port :default))
  ([port conn-name]
   (let [conn (nrepl/connect :port port)
         client (nrepl/client conn 1000)]
     (swap! connections assoc conn-name {:conn conn
                                         :client client
                                         :port port})
     {:conn conn :client client :port port})))

(defn disconnect
  "Disconnect from a shadow-cljs nREPL server."
  ([]
   (disconnect :default))
  ([conn-name]
   (when-let [{:keys [conn]} (get @connections conn-name)]
     (.close conn)
     (swap! connections dissoc conn-name)
     true)))

(defn get-connection
  "Get an existing connection by name."
  ([]
   (get-connection :default))
  ([conn-name]
   (get @connections conn-name)))

;; =============================================================================
;; Basic Operations
;; =============================================================================

(defn eval-clj
  "Evaluate Clojure code on the shadow-cljs server."
  [client code]
  (let [response (nrepl/message client {:op "eval" :code code})]
    (reduce (fn [acc msg]
              (cond
                (:value msg) (update acc :values (fnil conj []) (:value msg))
                (:out msg) (update acc :out str (:out msg))
                (:err msg) (update acc :err str (:err msg))
                :else acc))
            {}
            response)))

(defn setup-api
  "Initialize the shadow-cljs API in the nREPL session."
  [client]
  (eval-clj client "(require '[shadow.cljs.devtools.api :as api])"))

(defn get-build-info
  "Get information about a shadow-cljs build."
  [client build-id]
  (let [code (format "(do 
                        (require '[shadow.cljs.devtools.api :as api])
                        {:build-config (api/get-build-config %s)
                         :active? (contains? (api/active-builds) %s)
                         :worker-running? (api/worker-running? %s)
                         :runtime-count (count (api/repl-runtimes %s))})"
                     build-id build-id build-id build-id)]
    (eval-clj client code)))

;; =============================================================================
;; ClojureScript Evaluation
;; =============================================================================

(defn eval-cljs
  "Evaluate ClojureScript code in a shadow-cljs build.
   Requires the Node.js runtime to be connected for JS interop."
  ([client build-id code]
   (eval-cljs client build-id code {}))
  ([client build-id code opts]
   (let [escaped-code (str/replace code "\"" "\\\"")
         eval-code (format "(do 
                             (require '[shadow.cljs.devtools.api :as api])
                             (api/cljs-eval %s \"%s\" %s))"
                           build-id escaped-code (pr-str opts))
         response (eval-clj client eval-code)]
     ;; Parse the cljs-eval response
     (when-let [value (first (:values response))]
       (let [result (read-string value)]
         {:output (:out result)
          :error (:err result)
          :results (:results result)
          :ns (:ns result)})))))

(defn eval-cljs-sync
  "Synchronously evaluate ClojureScript and return the result."
  [client build-id code]
  (let [result (eval-cljs client build-id code)]
    (first (:results result))))

;; =============================================================================
;; Runtime Management
;; =============================================================================

(defn check-runtime
  "Check if a JavaScript runtime is connected to the build."
  [client build-id]
  (let [code (format "(do 
                        (require '[shadow.cljs.devtools.api :as api])
                        (count (api/repl-runtimes %s)))"
                     build-id)
        response (eval-clj client code)]
    (when-let [value (first (:values response))]
      (> (read-string value) 0))))

(defn ensure-runtime
  "Ensure a JavaScript runtime is connected. 
   If not, attempts to start the Node.js process."
  [client build-id output-path]
  (when-not (check-runtime client build-id)
    (println "No runtime connected. Starting Node.js process...")
    (let [code (format "(do
                          (require '[clojure.java.shell :as sh])
                          (future (sh/sh \"node\" \"%s\"))
                          :starting)"
                       output-path)]
      (eval-clj client code)
      ;; Wait for runtime to connect
      (Thread/sleep 2000)
      (check-runtime client build-id))))

(defn start-runtime
  "Start the Node.js runtime for a shadow-cljs build."
  [client output-path]
  (let [code (format "(do
                        (require '[clojure.java.shell :as sh])
                        (future (sh/sh \"node\" \"%s\"))
                        :started)"
                     output-path)]
    (eval-clj client code)))

;; =============================================================================
;; Build Operations
;; =============================================================================

(defn compile-build
  "Compile a shadow-cljs build."
  [client build-id]
  (let [code (format "(do 
                        (require '[shadow.cljs.devtools.api :as api])
                        (api/compile %s))"
                     build-id)]
    (eval-clj client code)))

(defn watch-build
  "Watch a shadow-cljs build for changes."
  [client build-id]
  (let [code (format "(do 
                        (require '[shadow.cljs.devtools.api :as api])
                        (api/watch %s))"
                     build-id)]
    (eval-clj client code)))

(defn stop-build
  "Stop watching a shadow-cljs build."
  [client build-id]
  (let [code (format "(do 
                        (require '[shadow.cljs.devtools.api :as api])
                        (api/stop-worker %s))"
                     build-id)]
    (eval-clj client code)))

;; =============================================================================
;; Testing & Diagnostics
;; =============================================================================

(defn test-connection
  "Test the connection to a shadow-cljs server and ClojureScript evaluation."
  [port build-id]
  (println "\n=== Testing Shadow-CLJS Connection ===")
  (println "Port:" port "Build:" build-id)
  (println "=====================================\n")

  (let [{:keys [client]} (connect port :test)]
    (try
      ;; Test basic connection
      (println "1. Testing basic connection...")
      (let [result (eval-clj client "(+ 1 2 3)")]
        (println "   Basic math:" (first (:values result)))
        (assert (= "6" (first (:values result)))))

      ;; Setup API
      (println "2. Setting up shadow-cljs API...")
      (setup-api client)
      (println "   API loaded")

      ;; Check build info
      (println "3. Checking build info...")
      (let [info (get-build-info client build-id)
            parsed (read-string (first (:values info)))]
        (println "   Active:" (:active? parsed))
        (println "   Worker running:" (:worker-running? parsed))
        (println "   Runtime count:" (:runtime-count parsed)))

      ;; Test ClojureScript evaluation
      (println "4. Testing ClojureScript evaluation...")
      (let [result (eval-cljs client build-id "(+ 10 20 30)")]
        (println "   Result:" (first (:results result))))

      ;; Test JS interop if runtime is connected
      (if (check-runtime client build-id)
        (do
          (println "5. Testing JavaScript interop...")
          (let [result (eval-cljs client build-id
                                  "(str \"Node version: \" js/process.version)")]
            (println "   " (first (:results result)))))
        (println "5. JavaScript runtime not connected (run Node.js process)"))

      (println "\n✅ All tests passed!")

      (finally
        (disconnect :test)))))

(defn diagnose
  "Diagnose issues with shadow-cljs connection and runtime."
  [port build-id]
  (println "\n=== Shadow-CLJS Diagnostics ===")
  (println "Port:" port "Build:" build-id)
  (println "================================\n")

  (let [{:keys [client]} (connect port :diagnose)]
    (try
      ;; Check connection
      (print "Checking nREPL connection... ")
      (let [result (eval-clj client "(+ 1 1)")]
        (if (= "2" (first (:values result)))
          (println "✅ Connected")
          (println "❌ Connection issue")))

      ;; Check shadow-cljs API
      (print "Loading shadow-cljs API... ")
      (setup-api client)
      (println "✅ Loaded")

      ;; Check build status
      (println "\nBuild Status:")
      (let [info (get-build-info client build-id)
            parsed (read-string (first (:values info)))]
        (println "  Config:" (if (:build-config parsed) "✅ Found" "❌ Not found"))
        (println "  Active:" (if (:active? parsed) "✅ Yes" "❌ No"))
        (println "  Worker:" (if (:worker-running? parsed) "✅ Running" "❌ Not running"))
        (println "  Runtime:" (if (> (:runtime-count parsed) 0)
                                (str "✅ Connected (" (:runtime-count parsed) ")")
                                "❌ Not connected")))

      ;; Check file system
      (println "\nFile System:")
      (let [code (format "(do
                           (require '[clojure.java.io :as io])
                           (.exists (io/file \"target/dev/app.js\")))")
            result (eval-clj client code)]
        (println "  app.js exists:"
                 (if (= "true" (first (:values result)))
                   "✅ Yes"
                   "❌ No")))

      ;; Check Node.js process
      (println "\nNode.js Process:")
      (let [code "(do 
                   (require '[clojure.java.shell :as sh])
                   (:out (sh/sh \"pgrep\" \"-fl\" \"node.*app.js\")))"
            result (eval-clj client code)
            output (read-string (first (:values result)))]
        (if (str/blank? output)
          (println "  ❌ No Node.js process found")
          (println "  ✅ Node.js running:\n    " (str/trim output))))

      (println "\n=== Diagnostics Complete ===")

      (finally
        (disconnect :diagnose)))))

;; =============================================================================
;; High-level API
;; =============================================================================

(defn with-shadow-cljs
  "Execute a function with a shadow-cljs connection.
   Automatically connects, sets up API, and disconnects."
  [port build-id f]
  (let [{:keys [client]} (connect port :temp)]
    (try
      (setup-api client)
      (f client build-id)
      (finally
        (disconnect :temp)))))

(defn quick-eval
  "Quickly evaluate ClojureScript code without managing connections."
  [port build-id code]
  (with-shadow-cljs port build-id
    (fn [client build-id]
      (eval-cljs-sync client build-id code))))

;; =============================================================================
;; REPL Enhancement
;; =============================================================================

(defn start-cljs-repl
  "Start an interactive ClojureScript REPL session."
  [port build-id]
  (let [{:keys [client]} (connect port :repl)]
    (setup-api client)
    (println "\n=== ClojureScript REPL ===")
    (println "Connected to shadow-cljs on port" port)
    (println "Build:" build-id)
    (println "Type :quit to exit\n")

    (loop []
      (print "cljs=> ")
      (flush)
      (let [input (read-line)]
        (when-not (= ":quit" input)
          (try
            (let [result (eval-cljs client build-id input)]
              (when (:output result)
                (print (:output result)))
              (when (seq (:results result))
                (doseq [r (:results result)]
                  (pp/pprint (read-string r))))
              (when (:error result)
                (println "Error:" (:error result))))
            (catch Exception e
              (println "Error:" (.getMessage e))))
          (recur))))

    (disconnect :repl)
    (println "REPL session ended.")))

;; =============================================================================
;; Usage Examples
;; =============================================================================

(comment
  ;; Basic usage
  (def conn (connect 7889))
  (setup-api (:client conn))
  (eval-cljs (:client conn) :dev "(+ 1 2 3)")
  (disconnect)

  ;; Quick evaluation
  (quick-eval 7889 :dev "(str \"Hello, \" \"World!\")")

  ;; Test connection
  (test-connection 7889 :dev)

  ;; Diagnose issues
  (diagnose 7889 :dev)

  ;; Interactive REPL
  (start-cljs-repl 7889 :dev)

  ;; With automatic connection management
  (with-shadow-cljs 7889 :dev
    (fn [client build-id]
      (println "Node version:"
               (eval-cljs-sync client build-id "js/process.version")))))
