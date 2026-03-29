#!/bin/bash

# The port your aichat application is running on
PORT=8080
LOG_FILE="test_log.txt"

# Clean log file
> $LOG_FILE

# An array of questions for the tech assignment
QUESTIONS=(
    "I want you to act as a senior software engineer. Your task is to create a technical plan for a new feature: a Wish List for our e-commerce website. First, describe the high-level architecture. What are the main components?"
    "Good. Now, let's focus on the backend. What would be the database schema for the wish list feature? Please provide the table structure with column names and types."
    "Okay, let's move to the API. Please define the RESTful API endpoints for the wish list. Include the HTTP methods, URL paths, and a brief description of each endpoint."
    "Now, let's consider the frontend. How would you implement the 'Add to Wish List' button on the product page? Describe the component and its interaction with the backend."
    "Finally, let's talk about scalability. What are some potential performance bottlenecks for the wish list feature, and how would you address them?"
    "Based on our conversation, can you summarize the database schema you proposed earlier?"
    "And what were the API endpoints we discussed?"
)

# The strategies to test
STRATEGIES=("unlimited" "sliding 3" "sticky")

# A function to send a message to the chat API
send_message() {
    local message=$1
    # Use Python to create a valid JSON payload
    json_payload=$(python3 -c 'import json, sys; print(json.dumps({"prompt": sys.argv[1]}))' "$message")
    
    curl -s -X POST http://localhost:$PORT/chat \
       -H "Content-Type: application/json" \
       -d "$json_payload"
}

# The main test loop
for strategy in "${STRATEGIES[@]}"; do
    echo "=============================================" | tee -a $LOG_FILE
    echo "Testing strategy: $strategy" | tee -a $LOG_FILE
    echo "=============================================" | tee -a $LOG_FILE

    # Set the strategy for the chat
    send_message "/strategy $strategy" >> /dev/null

    total_tokens_for_strategy=0

    # Ask the sequence of questions
    for question in "${QUESTIONS[@]}"; do
        echo ">> User: $question" | tee -a $LOG_FILE
        response_json=$(send_message "$question")
        
        response_message=$(echo $response_json | jq -r '.agentResponse')
        prompt_tokens=$(echo $response_json | jq -r '.promptTokens')
        completion_tokens=$(echo $response_json | jq -r '.completionTokens')
        total_tokens=$(echo $response_json | jq -r '.totalTokens')

        echo "<< Assistant: $response_message" | tee -a $LOG_FILE
        echo "[Tokens: Prompt=$prompt_tokens, Completion=$completion_tokens, Total=$total_tokens]" | tee -a $LOG_FILE
        echo "" | tee -a $LOG_FILE

        if [[ "$total_tokens" != "null" && "$total_tokens" -gt 0 ]]; then
            total_tokens_for_strategy=$((total_tokens_for_strategy + total_tokens))
        fi
    done

    echo "Total tokens for strategy '$strategy': $total_tokens_for_strategy" | tee -a $LOG_FILE
    echo "" | tee -a $LOG_FILE

    # Clean the history for the next run
    send_message "/clean" >> /dev/null
done

echo "Test completed. Full log is in $LOG_FILE"
