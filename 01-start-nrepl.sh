#!/usr/bin/env bash
set -eou pipefail
echo "About to start the clojure-mcp nrepl process"
cd /Users/choomnuanb/dev/clojure-mcp
clojure -M:nrepl
