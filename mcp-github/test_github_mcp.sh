#!/bin/bash

# Test script for GitHub MCP Server
# Usage: ./test_github_mcp.sh

echo "========================================="
echo "  GitHub MCP Server Test"
echo "========================================="
echo ""

# Check if GITHUB_TOKEN is set
if [ -z "$GITHUB_TOKEN" ]; then
    echo "ERROR: GITHUB_TOKEN environment variable is not set"
    echo "Set it with: export GITHUB_TOKEN=your_token_here"
    exit 1
fi

echo "✓ GITHUB_TOKEN is set"

# Build the server
echo ""
echo "Building GitHub MCP Server..."
cd "$(dirname "$0")"
mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to build GitHub MCP Server"
    exit 1
fi

echo "✓ Build successful"
echo ""
echo "Starting GitHub MCP Server on port 8083..."
echo "Press Ctrl+C to stop"
echo ""

mvn spring-boot:run
