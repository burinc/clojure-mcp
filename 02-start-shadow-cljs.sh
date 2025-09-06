#!/usr/bin/env bash
set -eo pipefail
echo "About to start the shadow-cljs watch server..."
cd /Users/choomnuanb/dev/github--chr15m--sitefox/examples/shadow-cljs-extended
npx shadow-cljs watch server client
