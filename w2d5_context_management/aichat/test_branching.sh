#!/bin/bash

# The port your aichat application is running on
PORT=8080
LOG_FILE="branch_test_log.txt"

# Clean log file
> $LOG_FILE

# An array of questions for the tech assignment
QUESTIONS=(
    "I want you to act as a senior software engineer. Your task is to create a technical plan for a new feature: a Wish List for our e-commerce website. First, describe the high-level architecture. What are the main components?"
    "Good. Now, let's focus on the backend. What would be the database schema for the wish list feature? Please provide the table structure with column names and types."
    "Okay, let's move to the API. Please define the RESTful API endpoints for the wish list. Include the HTTP methods, URL paths, and a brief description of each endpoint."
    "Now, let's consider the frontend. How would you implement the 'Add to Wish List' button on the product page? Describe the component and its interaction with the backend."
    "Based on our conversation, can you summarize the database schema you proposed earlier?"
)

# A function to send a message to the chat API
send_message() {
    local message=$1
    # Use Python to create a valid JSON payload
    json_payload=$(python3 -c 'import json, sys; print(json.dumps({"prompt": sys.argv[1]}))' "$message")
    
    curl -s -X POST http://localhost:$PORT/chat \
       -H "Content-Type: application/json" \
       -d "$json_payload"
}

# A function to log and print messages
log() {
    echo "$1" | tee -a $LOG_FILE
}

log "## Test Setup ##"
log "Cleaning history and setting strategy to unlimited..."
send_message "/clean" >> /dev/null
send_message "/strategy unlimited" >> /dev/null
log "Setup complete. Starting test in Branch 1."
log ""

log "============================================="
log "## Conversation in Branch 1 ##"
log "============================================="

log ">> User: ${QUESTIONS[0]}"
response_json=$(send_message "${QUESTIONS[0]}")
log "<< Assistant: $(echo $response_json | jq -r '.agentResponse')"
log ""

log ">> User: ${QUESTIONS[1]}"
response_json=$(send_message "${QUESTIONS[1]}")
log "<< Assistant: $(echo $response_json | jq -r '.agentResponse')"
log ""

log "============================================="
log "## Creating a new branch ##"
log "============================================="
log ">> User: /branch"
response_json=$(send_message "/branch")
log "<< Assistant: $(echo $response_json | jq -r '.agentResponse')"
log "Now in Branch 2. The history from Branch 1 (up to this point) has been inherited."
log ""


log "============================================="
log "## Conversation in Branch 2 ##"
log "============================================="
log "Continuing the conversation in the new branch..."

log ">> User: ${QUESTIONS[2]}"
response_json=$(send_message "${QUESTIONS[2]}")
log "<< Assistant: $(echo $response_json | jq -r '.agentResponse')"
log ""

log "Now, let's test the inherited context by asking a question about the earlier part of the conversation."
log ">> User: ${QUESTIONS[4]}"
response_json=$(send_message "${QUESTIONS[4]}")
log "<< Assistant: $(echo $response_json | jq -r '.agentResponse')"
log "The assistant should be able to answer correctly, demonstrating that the context was carried over to Branch 2."
log ""


log "============================================="
log "## Switching back to Branch 1 ##"
log "============================================="
log ">> User: /switch 1"
response_json=$(send_message "/switch 1")
log "<< Assistant: $(echo $response_json | jq -r '.agentResponse')"
log "Now back in Branch 1."
log ""

log "============================================="
log "## Continuing Conversation in Branch 1 ##"
log "============================================="
log "Let's ask a different question in Branch 1 to show the conversations have diverged."

log ">> User: ${QUESTIONS[3]}"
response_json=$(send_message "${QUESTIONS[3]}")
log "<< Assistant: $(echo $response_json | jq -r '.agentResponse')"
log "The assistant's response here is based on the context of Branch 1 only, without knowledge of the conversation in Branch 2."
log ""

log "Test completed. Full log is in $LOG_FILE"
