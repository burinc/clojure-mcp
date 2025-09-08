#!/usr/bin/env bash
set -eou pipefail

cd /Users/choomnuanb/dev/clojure-mcp

#clojure -X:mcp-shadow-dual \
#  :port 7888 \
#  :shadow-port 7889 \
#  :shadow-build '"server"' \
#  :project-dir '"/Users/choomnuanb/dev/github--chr15m--sitefox/examples/shadow-cljs-extended"'

# clojure -X:mcp-shadow-dual \
#   :port 7888 \
#   :shadow-port 7889 \
#   :shadow-build '"server"' \
#   :project-dir '"/Users/choomnuanb/dev/b12n-newsapi"'

clojure -X:mcp-shadow-dual \
  :port 7888 \
  :shadow-port 7889 \
  :shadow-build '"server"' \
  :project-dir '"/Users/choomnuanb/dev/b12n-cljsapp"'
