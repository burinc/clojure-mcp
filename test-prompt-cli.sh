#!/bin/bash

# Test script for the Clojure MCP Prompt CLI

echo "Testing Clojure MCP Prompt CLI..."
echo "================================="
echo ""

# Test help
echo "1. Testing help output:"
clojure -M:prompt-cli -h

echo ""
echo "2. Testing simple prompt (requires running nREPL server on port 7888):"
echo "   Command: clojure -M:prompt-cli -p \"(+ 1 2)\""
echo ""
echo "   To run this test:"
echo "   - Start nREPL in one terminal: clojure -M:nrepl"
echo "   - Run in another terminal: clojure -M:prompt-cli -p \"(+ 1 2)\""

echo ""
echo "3. Testing with custom model:"
echo "   clojure -M:prompt-cli -p \"What is Clojure?\" -m :openai/gpt-4"

echo ""
echo "4. Testing with custom port:"
echo "   clojure -M:prompt-cli -p \"List namespaces\" -P 8888"

echo ""
echo "Note: Actual execution requires:"
echo "  - Running nREPL server"
echo "  - Configured API keys in environment or .clojure-mcp/config.edn"
