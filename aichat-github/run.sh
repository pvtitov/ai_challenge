#!/bin/bash

# Run script for AI Chat GitHub - GitHub Agent
# This script automatically:
# 1. Builds and starts the GitHub MCP server (port 8083)
# 2. Starts the aichat-github client
# 3. Cleans up MCP server when client exits

# Configuration
MCP_GITHUB_DIR="/Users/paveltitov/Documents/programming/ai_challenge/mcp-github"
AICHAT_GITHUB_DIR="$(cd "$(dirname "$0")" && pwd)"
MCP_PORT=8083
MCP_PID=""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Function to cleanup MCP server on exit
cleanup() {
    echo -e "\n${YELLOW}Shutting down GitHub MCP server...${NC}"
    if [ ! -z "$MCP_PID" ]; then
        kill $MCP_PID 2>/dev/null
        wait $MCP_PID 2>/dev/null
        echo -e "${GREEN}✓ GitHub MCP server stopped${NC}"
    fi
    exit 0
}

# Trap SIGINT and SIGTERM for cleanup
trap cleanup SIGINT SIGTERM EXIT

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  AI Chat GitHub - GitHub Agent        ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Step 1: Build GitHub MCP Server
echo -e "${YELLOW}Step 1: Building GitHub MCP Server...${NC}"
cd "$MCP_GITHUB_DIR"
mvn clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Failed to build GitHub MCP Server${NC}"
    echo -e "${YELLOW}Continuing without MCP server...${NC}"
    MCP_AVAILABLE=false
else
    echo -e "${GREEN}✓ GitHub MCP Server built successfully${NC}"
    MCP_AVAILABLE=true
fi

# Step 2: Start GitHub MCP Server (if available)
if [ "$MCP_AVAILABLE" = true ]; then
    echo -e "\n${YELLOW}Step 2: Starting GitHub MCP Server on port $MCP_PORT...${NC}"
    cd "$MCP_GITHUB_DIR"
    mvn spring-boot:run &
    MCP_PID=$!
    echo "GitHub MCP Server PID: $MCP_PID"

    # Wait for MCP server to start
    echo -n "Waiting for GitHub MCP server"
    MCP_STARTED=false
    for i in {1..30}; do
        sleep 1
        echo -n "."
        if curl -s http://localhost:$MCP_PORT >/dev/null 2>&1; then
            echo -e "\n${GREEN}✓ GitHub MCP Server is running${NC}"
            MCP_STARTED=true
            break
        fi
        if [ $i -eq 30 ]; then
            echo -e "\n${RED}✗ GitHub MCP Server failed to start within 30 seconds${NC}"
            echo -e "${YELLOW}Continuing without MCP server...${NC}"
            MCP_PID=""
        fi
    done
fi

cd "$AICHAT_GITHUB_DIR"

# Step 3: Build aichat-github
echo -e "\n${YELLOW}Step 3: Building aichat-github...${NC}"
mvn clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Failed to build aichat-github${NC}"
    exit 1
fi
echo -e "${GREEN}✓ aichat-github built successfully${NC}"

# Step 4: Run aichat-github
echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  Starting AI Chat GitHub...             ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}Make sure Ollama is running on localhost:11434${NC}"
if [ ! -z "$MCP_PID" ]; then
    echo -e "${YELLOW}GitHub MCP server is running on localhost:$MCP_PORT${NC}"
fi
echo ""

# Save MCP PID to file for cleanup
if [ ! -z "$MCP_PID" ]; then
    echo "$MCP_PID" > /tmp/aichat-github-mcp.pid
fi

java -jar target/aichat-github-1.0.jar
JAVA_EXIT_CODE=$?

# Clean up MCP server when Java exits
if [ -f /tmp/aichat-github-mcp.pid ]; then
    SAVED_MCP_PID=$(cat /tmp/aichat-github-mcp.pid)
    if [ ! -z "$SAVED_MCP_PID" ]; then
        echo -e "\n${YELLOW}Shutting down GitHub MCP server (PID: $SAVED_MCP_PID)...${NC}"
        kill $SAVED_MCP_PID 2>/dev/null
        wait $SAVED_MCP_PID 2>/dev/null
        echo -e "${GREEN}✓ GitHub MCP server stopped${NC}"
    fi
    rm -f /tmp/aichat-github-mcp.pid
fi

exit $JAVA_EXIT_CODE

# When java exits, cleanup will be triggered by the trap

