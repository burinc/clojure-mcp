# Shadow-CLJS Integration Quick Start

## Setup Instructions

### 1. Make the test script executable
```bash
chmod +x test-shadow-cljs.sh
```

### 2. Start your shadow-cljs project
```bash
cd /Users/choomnuanb/dev/shadow-cljs-nodejs-demo
./01-start-shadow-cljs.sh    # Starts shadow-cljs server on port 7889
./02-run-nodejs.sh           # Starts Node.js runtime
```

### 3. Test the connection
From the clojure-mcp directory:
```bash
./test-shadow-cljs.sh 7889 dev
```

## Quick Usage from Clojure REPL

### Basic Connection Test
```clojure
;; Load the helper namespace
(require '[clojure-mcp.tools.shadow-cljs.helper :as shadow])

;; Run diagnostic
(shadow/diagnose 7889 :dev)

;; Test the connection
(shadow/test-connection 7889 :dev)
```

### Evaluate ClojureScript
```clojure
;; Quick evaluation
(shadow/quick-eval 7889 :dev "(+ 1 2 3)")

;; With JS interop
(shadow/quick-eval 7889 :dev "js/process.version")

;; Complex evaluation
(shadow/quick-eval 7889 :dev 
  "(do 
     (def fs (js/require \"fs\"))
     (vec (.readdirSync fs \".\")))")
```

### Interactive REPL Session
```clojure
;; Start an interactive ClojureScript REPL
(shadow/start-cljs-repl 7889 :dev)
;; Type :quit to exit
```

### Manual Connection Management
```clojure
;; Connect
(def conn (shadow/connect 7889))

;; Setup API
(shadow/setup-api (:client conn))

;; Check runtime
(shadow/check-runtime (:client conn) :dev)

;; Evaluate ClojureScript
(shadow/eval-cljs (:client conn) :dev "(println \"Hello!\")")

;; Disconnect when done
(shadow/disconnect)
```

### Using with-shadow-cljs macro
```clojure
(shadow/with-shadow-cljs 7889 :dev
  (fn [client build-id]
    ;; Your code here
    (println "Node version:" 
             (shadow/eval-cljs-sync client build-id "js/process.version"))
    (println "Current dir:"
             (shadow/eval-cljs-sync client build-id "(js/process.cwd)"))))
```

## Troubleshooting

### No runtime connected
If you see "No available JS runtime", run your Node.js script:
```bash
cd /Users/choomnuanb/dev/shadow-cljs-nodejs-demo
./02-run-nodejs.sh
```

### Connection refused
Make sure shadow-cljs is running:
```bash
cd /Users/choomnuanb/dev/shadow-cljs-nodejs-demo
./01-start-shadow-cljs.sh
```

### Check if services are running
```bash
# Check shadow-cljs nREPL
lsof -i :7889

# Check Node.js process
ps aux | grep "node.*app.js"
```

## Files Created

1. **Documentation**: `docs/shadow-cljs-integration.md`
   - Complete guide for shadow-cljs integration
   - Troubleshooting tips
   - API reference

2. **Helper Library**: `src/clojure_mcp/tools/shadow_cljs/helper.clj`
   - Connection management functions
   - ClojureScript evaluation helpers
   - Diagnostic tools
   - Interactive REPL support

3. **Test Script**: `test-shadow-cljs.sh`
   - Quick connection test
   - Runtime verification
   - Basic evaluation test

## Next Steps

1. Start using the helper functions in your REPL:
   ```clojure
   (require '[clojure-mcp.tools.shadow-cljs.helper :as shadow])
   ```

2. Run diagnostics to ensure everything is working:
   ```clojure
   (shadow/diagnose 7889 :dev)
   ```

3. Start evaluating ClojureScript code:
   ```clojure
   (shadow/quick-eval 7889 :dev "(println \"It works!\")")
   ```

Enjoy your ClojureScript development with shadow-cljs!
