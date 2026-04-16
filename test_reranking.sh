#!/bin/bash

# Test script to compare RAG quality with different reranking and query rewriting configurations.

set -e

AICHAT_URL="http://localhost:8080"
APP_PROPERTIES_PATH="aichat/src/main/resources/application.properties"
RUN_SERVERS_SCRIPT="./run_servers.sh"

QUESTIONS=(
    "Tell me about C programming language and best way to learn it?"
    "What is Duff's device?"
    "What do you know about undefined behavior?"
    "How to handle an undefined behavior?"
)

# Define scenarios with their property settings
SCENARIOS=(
    "Baseline|embedding.queryRewrite.enabled=false embedding.topKBeforeRerank=10 embedding.topKAfterRerank=10 embedding.similarityThreshold=0.0"
    "Strict Filtering|embedding.queryRewrite.enabled=false embedding.topKBeforeRerank=20 embedding.topKAfterRerank=5 embedding.similarityThreshold=0.75"
    "Query Rewriting|embedding.queryRewrite.enabled=true embedding.topKBeforeRerank=10 embedding.topKAfterRerank=5 embedding.similarityThreshold=0.6"
    "Combined (Rewriting + Filtering)|embedding.queryRewrite.enabled=true embedding.topKBeforeRerank=20 embedding.topKAfterRerank=5 embedding.similarityThreshold=0.75"
)

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# --- Helper Functions ---

# Function to update application.properties
update_application_properties() {
    local prop_keys=()
    local prop_values=()
    for prop_pair in "$@"; do
        prop_keys+=("$(echo "$prop_pair" | cut -d'=' -f1)")
        prop_values+=("$(echo "$prop_pair" | cut -d'=' -f2)")
    done

    echo -e "${YELLOW}Updating ${APP_PROPERTIES_PATH} with: $@${NC}"

    # Read current properties
    local current_props
    if [ -f "$APP_PROPERTIES_PATH" ]; then
        current_props=$(cat "$APP_PROPERTIES_PATH")
    else
        current_props=""
    fi

    # Update existing or add new properties
    for i in "${!prop_keys[@]}"; do
        key="${prop_keys[$i]}"
        value="${prop_values[$i]}"
        if echo "$current_props" | grep -q "^${key}="; then
            # Replace existing line
            current_props=$(echo "$current_props" | sed "s|^${key}=.*|${key}=${value}|")
        else
            # Add new line
            current_props="${current_props}\n${key}=${value}"
        fi
    done
    
    echo -e "$current_props" > "$APP_PROPERTIES_PATH"
}

# Function to start all servers using run_servers.sh
start_all_servers() {
    echo -e "${YELLOW}Starting all servers using ${RUN_SERVERS_SCRIPT}...${NC}"
    # Ensure the script is executable
    chmod +x "$RUN_SERVERS_SCRIPT"
    
    # Start run_servers.sh in the background in a subshell to create a new process group
    ( "$RUN_SERVERS_SCRIPT" > run_servers_stdout.log 2> run_servers_stderr.log & )
    SERVERS_PID=$!
    echo "Servers PID: $SERVERS_PID"

    echo -n "Waiting for AIChat to become available"
    for i in {1..60}; do # Increased timeout
        sleep 1
        echo -n "."
        if curl -s "$AICHAT_URL" --max-time 3 > /dev/null 2>&1; then
            echo -e "\n${GREEN}✓ AIChat server is running${NC}"
            return 0
        fi
    done
    echo -e "\n${RED}✗ AIChat server failed to start within 60 seconds${NC}"
    return 1
}

# Function to kill all servers
kill_all_servers() {
    echo -e "${YELLOW}Stopping all servers (PID: $SERVERS_PID)...${NC}"
    if [ ! -z "$SERVERS_PID" ]; then
        # Send SIGINT to the process group to allow cleanup function in run_servers.sh to execute
        kill -SIGINT "$SERVERS_PID" 2>/dev/null
        # Give it some time to clean up
        sleep 5
        # If it's still running, force kill
        if ps -p "$SERVERS_PID" > /dev/null; then
            echo -e "${RED}Servers did not terminate gracefully, forcing kill.${NC}"
            kill -9 "$SERVERS_PID" 2>/dev/null
        fi
    fi
    echo -e "${GREEN}All servers stopped.${NC}"
}

# Function to send question and get response
send_question() {
    local question="$1"
    local scenario_name="$2"
    local rewritten_query_enabled="$3" # "true" or "false"

    echo -e "${GREEN}  Question: ${question}${NC}"
    
    local raw_response=$(curl -s -X POST "$AICHAT_URL/chat" \
        -H "Content-Type: application/json" \
        -d "{\"prompt\": \"$question\"}" \
        --max-time 120)

    local response_text=$(python3 -c "import json, sys; data=json.loads(sys.stdin.read()); print(data.get('response') or data.get('fullResponse') or data.get('plan') or str(data))" <<< "$(echo "$raw_response" | sed 's/{\"timestamp\":.*//')")

    if [ -z "$response_text" ]; then
        echo -e "${RED}  ✗ Failed to get response from AIChat.${NC}"
        return
    fi
    
    echo -e "${GREEN}  AIChat Response:${NC}"
    echo "$response_text"
    
    if [ "$rewritten_query_enabled" == "true" ]; then
        # Attempt to extract rewritten query from the stderr log of run_servers.sh
        # This assumes the log "Original query: '...' rewritten to: '...'" is present.
        local rewritten_query=$(grep "Original query: '.*?' rewritten to: '" run_servers_stderr.log | tail -n 1 | sed -n "s/.*Original query: '.*?' rewritten to: '\(.*\)'/\1/p")
        if [ ! -z "$rewritten_query" ]; then
            echo -e "${YELLOW}  Rewritten Query: ${rewritten_query}${NC}"
        else
            echo -e "${YELLOW}  (Rewritten query not found in logs or not rewritten for this request.)${NC}"
        fi
    fi
    echo ""
}

# --- Main Test Logic ---

echo -e "${GREEN}================================================================"
echo -e "  RERANKING AND QUERY REWRITING COMPARISON TEST"
echo -e "================================================================${NC}"
echo ""

# Store original application.properties content
ORIGINAL_PROPERTIES_CONTENT=""
if [ -f "$APP_PROPERTIES_PATH" ]; then
    ORIGINAL_PROPERTIES_CONTENT=$(cat "$APP_PROPERTIES_PATH")
fi

# Cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}--- Cleaning up ---${NC}"
    if [ ! -z "$SERVERS_PID" ]; then
        kill_all_servers
    fi
    # Restore original application.properties
    if [ ! -z "$ORIGINAL_PROPERTIES_CONTENT" ]; then
        echo -e "${YELLOW}Restoring original ${APP_PROPERTIES_PATH}...${NC}"
        echo -e "$ORIGINAL_PROPERTIES_CONTENT" > "$APP_PROPERTIES_PATH"
        echo -e "${GREEN}Original properties restored.${NC}"
    fi
    # Clean up log files
    rm -f run_servers_stdout.log run_servers_stderr.log
    echo -e "${GREEN}Test cleanup complete.${NC}"
}
trap cleanup EXIT

# Check if Ollama is running
echo -e "${YELLOW}=== Checking if Ollama is running ===${NC}"
if ! curl -s "http://localhost:11434/api/tags" --max-time 3 > /dev/null 2>&1; then
    echo -e "${RED}ERROR: Ollama is not running at http://localhost:11434${NC}"
    echo -e "${RED}Please start Ollama first (e.g., 'ollama serve')${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Ollama is running${NC}"
echo ""

SERVERS_PID="" # Initialize PID variable

for scenario_entry in "${SCENARIOS[@]}"; do
    scenario_name=$(echo "$scenario_entry" | cut -d'|' -f1)
    scenario_properties=$(echo "$scenario_entry" | cut -d'|' -f2)

    echo -e "${GREEN}================================================================"
    echo -e "  SCENARIO: ${scenario_name}"
    echo -e "================================================================${NC}"
    
    # Update properties for the current scenario
    update_application_properties $scenario_properties
    
    # Restart servers to apply new properties
    if [ ! -z "$SERVERS_PID" ]; then
        kill_all_servers
        sleep 5 # Give time for ports to free up
    fi
    
    if ! start_all_servers; then
        echo -e "${RED}Skipping scenario ${scenario_name} due to server startup failure.${NC}"
        continue
    fi
    
    sleep 20 # Give AIChat some extra time to fully initialize with new properties

    # Determine if query rewriting is enabled for this scenario
    REWRITE_ENABLED="false"
    if [[ "$scenario_properties" == *"embedding.queryRewrite.enabled=true"* ]]; then
        REWRITE_ENABLED="true"
    fi

    for i in "${!QUESTIONS[@]}"; do
        question="${QUESTIONS[$i]}"
        send_question "$question" "$scenario_name" "$REWRITE_ENABLED"
    done
    echo ""
done

echo -e "${GREEN}================================================================"
echo -e "  ALL SCENARIOS COMPLETED"
echo -e "================================================================${NC}"
echo ""

# Cleanup will be handled by the trap EXIT
