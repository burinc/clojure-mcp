#!/bin/bash

# Shadow-CLJS ClojureScript Evaluation Test Script
# Usage: ./test-shadow-cljs.sh [port] [build-id]

PORT=${1:-7889}
BUILD_ID=${2:-dev}

echo "================================================"
echo "Shadow-CLJS ClojureScript Evaluation Test"
echo "================================================"
echo "Port: $PORT"
echo "Build ID: $BUILD_ID"
echo ""

# Function to run Clojure code
run_clj() {
    clojure -e "$1" 2>/dev/null
}

# Test 1: Check if port is open
echo "1. Checking if nREPL port $PORT is open..."
if lsof -i :$PORT > /dev/null 2>&1; then
    echo "   ✅ Port $PORT is listening"
else
    echo "   ❌ Port $PORT is not open. Please start shadow-cljs server."
    exit 1
fi

# Test 2: Check Node.js process
echo ""
echo "2. Checking for Node.js runtime process..."
if pgrep -fl "node.*app.js" > /dev/null 2>&1; then
    echo "   ✅ Node.js process is running"
    pgrep -fl "node.*app.js" | sed 's/^/   /'
else
    echo "   ⚠️  No Node.js process found. JS interop will not work."
    echo "   Run your 02-run-nodejs.sh script to start it."
fi

# Test 3: Test ClojureScript evaluation
echo ""
echo "3. Testing ClojureScript evaluation..."
echo ""

cat << EOF | clojure -
(require '[nrepl.core :as nrepl])

(defn test-shadow-cljs [port build-id]
  (let [conn (nrepl/connect :port port)
        client (nrepl/client conn 1000)]
    
    ;; Setup API
    (println "   Setting up shadow-cljs API...")
    (let [response (nrepl/message client 
                    {:op "eval" 
                     :code "(require '[shadow.cljs.devtools.api :as api])"})]
      (if (some :err response)
        (println "   ❌ Failed to load API")
        (println "   ✅ API loaded")))
    
    ;; Check runtime
    (println "\n   Checking runtime connection...")
    (let [response (nrepl/message client 
                    {:op "eval"
                     :code (str "(count (api/repl-runtimes " build-id "))")})]
      (if-let [count (some :value response)]
        (if (> (read-string count) 0)
          (println (str "   ✅ Runtime connected (count: " count ")"))
          (println "   ❌ No runtime connected"))
        (println "   ❌ Failed to check runtime")))
    
    ;; Test basic ClojureScript
    (println "\n   Testing ClojureScript evaluation...")
    (let [response (nrepl/message client
                    {:op "eval"
                     :code (str "(api/cljs-eval " build-id " "
                               "\\"(+ 1 2 3 4 5)\\" {})")})]
      (if-let [value (some :value response)]
        (let [result (read-string value)]
          (if (:results result)
            (println (str "   ✅ Math test: (+ 1 2 3 4 5) = " 
                         (first (:results result))))
            (println "   ❌ No results returned")))
        (println "   ❌ Evaluation failed")))
    
    ;; Test JS interop
    (println "\n   Testing JavaScript interop...")
    (let [response (nrepl/message client
                    {:op "eval"
                     :code (str "(api/cljs-eval " build-id " "
                               "\\"js/process.version\\" {})")})]
      (if-let [value (some :value response)]
        (let [result (read-string value)]
          (cond
            (:results result)
            (println (str "   ✅ Node.js version: " 
                         (first (:results result))))
            
            (re-find #"No available JS runtime" (:err result))
            (println "   ⚠️  JS runtime not connected (run Node.js process)")
            
            :else
            (println "   ❌ JS interop failed")))
        (println "   ❌ Could not test JS interop")))
    
    (.close conn)
    (println "\n================================================")
    (println "Test complete!")))

;; Run the test
(test-shadow-cljs $PORT :$BUILD_ID)
EOF

echo ""
echo "For more detailed testing, you can use the helper namespace:"
echo "  (require '[clojure-mcp.tools.shadow-cljs.helper :as shadow])"
echo "  (shadow/test-connection $PORT :$BUILD_ID)"
echo "  (shadow/diagnose $PORT :$BUILD_ID)"
