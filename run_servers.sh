#!/bin/bash

# Test script to run both aichat and mcp-test-server
# This script starts both servers and verifies MCP connectivity

AICHAT_DIR="/Users/paveltitov/Documents/programming/ai_challenge/aichat"
MCP_SERVER_DIR="/Users/paveltitov/Documents/programming/ai_challenge/mcp-test-server"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  AIChat & MCP Test Server Launcher    ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Function to cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Shutting down servers...${NC}"
    if [ ! -z "$MCP_PID" ]; then
        kill $MCP_PID 2>/dev/null
        echo "MCP test server stopped"
    fi
    if [ ! -z "$AICHAT_PID" ]; then
        kill $AICHAT_PID 2>/dev/null
        echo "AIChat server stopped"
    fi
    exit 0
}

# Trap SIGINT and SIGTERM
trap cleanup SIGINT SIGTERM

# Step 1: Build both projects
echo -e "${YELLOW}Step 1: Building MCP Test Server...${NC}"
cd "$MCP_SERVER_DIR"
mvn clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to build MCP test server${NC}"
    exit 1
fi
echo -e "${GREEN}✓ MCP Test Server built successfully${NC}"

echo -e "\n${YELLOW}Step 2: Building AIChat...${NC}"
cd "$AICHAT_DIR"
mvn clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to build aichat${NC}"
    exit 1
fi
echo -e "${GREEN}✓ AIChat built successfully${NC}"

# Step 2: Start MCP Test Server
echo -e "\n${YELLOW}Step 3: Starting MCP Test Server on port 8081...${NC}"
cd "$MCP_SERVER_DIR"
mvn spring-boot:run &
MCP_PID=$!
echo "MCP Test Server PID: $MCP_PID"

# Wait for MCP server to start
echo -n "Waiting for MCP server"
for i in {1..30}; do
    sleep 1
    echo -n "."
    if curl -s http://localhost:8081/actuator/health >/dev/null 2>&1 || curl -s http://localhost:8081 >/dev/null 2>&1; then
        echo -e "\n${GREEN}✓ MCP Test Server is running${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "\n${RED}✗ MCP Test Server failed to start within 30 seconds${NC}"
        cleanup
    fi
done

# Step 3: Start AIChat
echo -e "\n${YELLOW}Step 4: Starting AIChat on port 8080...${NC}"
cd "$AICHAT_DIR"
mvn spring-boot:run &
AICHAT_PID=$!
echo "AIChat PID: $AICHAT_PID"

# Wait for aichat to start
echo -n "Waiting for AIChat"
for i in {1..30}; do
    sleep 1
    echo -n "."
    if curl -s http://localhost:8080 >/dev/null 2>&1; then
        echo -e "\n${GREEN}✓ AIChat is running${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "\n${RED}✗ AIChat failed to start within 30 seconds${NC}"
        cleanup
    fi
done

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  Both servers are running!              ${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "${YELLOW}MCP Test Server:${NC} http://localhost:8081"
echo -e "${YELLOW}AIChat Web Client:${NC} http://localhost:8080"
echo ""
echo -e "${YELLOW}Available MCP Commands:${NC}"
echo -e "  ${GREEN}/mcp_connect${NC}   - Connect to MCP server"
echo -e "  ${GREEN}/mcp_status${NC}  - Check connection status"
echo -e "  ${GREEN}/mcp_list${NC}    - List available tools, resources, and prompts"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop both servers${NC}"
echo ""

# Keep script running
wait
