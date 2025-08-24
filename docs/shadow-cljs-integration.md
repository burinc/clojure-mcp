# Shadow-CLJS Integration with Clojure-MCP

## Overview
This guide documents how to connect to and evaluate ClojureScript code in a shadow-cljs project using the clojure-mcp tools.

## Prerequisites

### 1. Shadow-CLJS Project Setup
Your shadow-cljs project needs:
- An nREPL server configured (typically on port 7889)
- A Node.js build target (`:node-script`)
- The compiled JavaScript output

### 2. Required Scripts
You need two scripts to start the shadow-cljs environment:

**01-start-shadow-cljs.sh** - Starts the shadow-cljs server with nREPL
**02-run-nodejs.sh** - Runs the compiled Node.js application

## Connection Process

### Step 1: Start Shadow-CLJS Server
```bash
cd /path/to/shadow-cljs-project
./01-start-shadow-cljs.sh
```
This starts the shadow-cljs server with nREPL on port 7889.

### Step 2: Run Node.js Runtime
```bash
./02-run-nodejs.sh
```
This runs the compiled JavaScript and connects the runtime to shadow-cljs.

### Step 3: Connect from Clojure-MCP

```clojure
;; 1. Connect to the nREPL server
(require '[nrepl.core :as nrepl])
(def conn (nrepl/connect :port 7889))
(def client (nrepl/client conn 1000))

;; 2. Require shadow-cljs API
(def setup-response 
  (nrepl/message client 
    {:op "eval"
     :code "(require '[shadow.cljs.devtools.api :as api])"}))

;; 3. Check runtime status
(def status-response
  (nrepl/message client
    {:op "eval" 
     :code "(api/repl-runtimes :dev)"}))

;; 4. Evaluate ClojureScript code
(def eval-response
  (nrepl/message client
    {:op "eval"
     :code "(api/cljs-eval :dev 
              \"(println \\\"Hello from ClojureScript!\\\")
               js/process.version\"
              {})"}))
```

## Common Issues and Solutions

### Issue: "No available JS runtime"
**Solution**: Ensure the Node.js process is running (step 2). The runtime must be connected for JavaScript interop to work.

### Issue: "Wrong number of args passed to cljs-eval"
**Solution**: The `api/cljs-eval` function requires 3 arguments:
1. Build ID (e.g., `:dev`)
2. Code string to evaluate
3. Options map (can be empty `{}`)

### Issue: "No such namespace: js"
**Solution**: This indicates the JavaScript runtime isn't connected. Check that:
1. The Node.js process is running
2. The build has been compiled
3. The runtime count is > 0

## Testing the Connection

Use this test function to verify everything is working:

```clojure
(defn test-shadow-cljs-connection [port build-id]
  (let [conn (nrepl/connect :port port)
        client (nrepl/client conn 1000)]
    
    ;; Setup
    (nrepl/message client 
      {:op "eval" 
       :code "(require '[shadow.cljs.devtools.api :as api])"})
    
    ;; Check runtime
    (let [runtime-check (nrepl/message client 
                         {:op "eval"
                          :code (str "(count (api/repl-runtimes " build-id "))")})]
      (println "Runtime count:" 
               (some :value runtime-check)))
    
    ;; Test evaluation
    (let [test-result (nrepl/message client
                       {:op "eval"
                        :code (str "(api/cljs-eval " build-id " 
                                     \"(str \\\"Node: \\\" js/process.version)\"
                                     {})")})]
      (doseq [msg test-result]
        (when (:value msg)
          (println "Result:" (:value msg)))))
    
    conn))

;; Usage:
;; (test-shadow-cljs-connection 7889 :dev)
```

## Helper Functions

See `src/clojure_mcp/tools/shadow_cljs/helper.clj` for reusable helper functions.

## Quick Reference

### Essential Commands

```clojure
;; Connect
(def conn (nrepl/connect :port 7889))
(def client (nrepl/client conn 1000))

;; Setup API
(nrepl/message client {:op "eval" :code "(require '[shadow.cljs.devtools.api :as api])"})

;; Check builds
(nrepl/message client {:op "eval" :code "(api/active-builds)"})

;; Check runtime
(nrepl/message client {:op "eval" :code "(api/repl-runtimes :dev)"})

;; Evaluate ClojureScript
(nrepl/message client {:op "eval" :code "(api/cljs-eval :dev \"(+ 1 2)\" {})"})

;; Compile build
(nrepl/message client {:op "eval" :code "(api/compile :dev)"})

;; Watch build
(nrepl/message client {:op "eval" :code "(api/watch :dev)"})
```

## Full Example Session

```clojure
;; 1. Connect and setup
(require '[nrepl.core :as nrepl])
(def conn (nrepl/connect :port 7889))
(def client (nrepl/client conn 1000))

;; 2. Initialize shadow-cljs API
(nrepl/message client 
  {:op "eval" 
   :code "(require '[shadow.cljs.devtools.api :as api])"})

;; 3. Check if Node.js runtime is connected
(def runtime-check 
  (nrepl/message client 
    {:op "eval" 
     :code "(count (api/repl-runtimes :dev))"}))
(println "Runtimes connected:" (some :value runtime-check))

;; 4. If no runtime, start Node.js process
(when (= "0" (some :value runtime-check))
  (println "Starting Node.js runtime...")
  (nrepl/message client 
    {:op "eval"
     :code "(future 
              (require '[clojure.java.shell :as sh])
              (sh/sh \"node\" \"target/dev/app.js\"))"}))

;; 5. Evaluate ClojureScript with full Node.js interop
(def result 
  (nrepl/message client 
    {:op "eval"
     :code "(api/cljs-eval :dev 
              \"(do 
                 (println \\\"System info:\\\")
                 (println \\\"Node version:\\\" js/process.version)
                 (println \\\"Platform:\\\" js/process.platform)
                 (println \\\"Directory:\\\" (js/process.cwd))
                 {:status :success})\"
              {})"}))

;; 6. Display results
(doseq [msg result]
  (when (:value msg)
    (let [data (read-string (:value msg))]
      (when (:out data) (print (:out data)))
      (when (:results data) 
        (println "Return value:" (first (:results data)))))))
```

## Troubleshooting Checklist

- [ ] Shadow-cljs server is running (port 7889 is listening)
- [ ] Node.js process is running (check with `ps aux | grep node`)
- [ ] Build is compiled (`target/dev/app.js` exists)
- [ ] Runtime is connected (runtime count > 0)
- [ ] Using correct build ID (e.g., `:dev`)
- [ ] Code strings are properly escaped
- [ ] Using 3 arguments for `api/cljs-eval`

## Additional Resources

- [Shadow-CLJS Documentation](https://shadow-cljs.github.io/docs/UsersGuide.html)
- [Shadow-CLJS REPL Troubleshooting](https://shadow-cljs.github.io/docs/UsersGuide.html#repl-troubleshooting)
- [nREPL Documentation](https://nrepl.org/)
