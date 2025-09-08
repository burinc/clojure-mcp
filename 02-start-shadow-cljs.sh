#!/usr/bin/env bash
set -eo pipefail
#echo "About to start the shadow-cljs watch server..."
#cd /Users/choomnuanb/dev/github--chr15m--sitefox/examples/shadow-cljs-extended

#echo "About to start the b12n-newsapi watch app..."
#cd /Users/choomnuanb/dev/b12n-newsapi
#npx shadow-cljs watch app

echo "About to start the b12n-cljsapp watch app..."
cd /Users/choomnuanb/dev/b12n-cljsapp
npx shadow-cljs watch app
