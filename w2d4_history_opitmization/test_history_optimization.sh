#!/bin/bash

# This script tests and compares two chat agent implementations by
# replicating their core logic to gain full control over the I/O.
# 1. Unoptimized: Sends the full chat history in each request.
# 2. Optimized: Summarizes history to reduce token count.

set -e # Exit immediately if a command exits with a non-zero status.

# Get the absolute path of the directory containing this script.
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
BASE_DIR="$SCRIPT_DIR/.."

# --- Configuration ---
UNOPTIMIZED_HISTORY_PATH="$BASE_DIR/w2d3_tokens_count/chat_history.json"
OPTIMIZED_HISTORY_PATH="$BASE_DIR/w2d4_history_opitmization/chat_history.json"
OPTIMIZE_SCRIPT="$BASE_DIR/w2d4_history_opitmization/optimize_history.sh"
REQUEST_GIGACHAT_PATH="$BASE_DIR/shared/request_gigachat.sh"

# Hardcoded list of prompts to test context retention.
PROMPTS=(
  "What are best practices to organize bash scripts to build a robust system?"                                                                                              │
  "What are pros and cons of bash?"                                                                                                                                         │
  "Whould you recommend to use bash to write an AI-agent?"                                                                                                                  │
  "Is it possible to create web service as bash script? Would you recommend it?"                                                                                            │
  "How to deply and run such service? Give instructions."                                                                                                                   │
  "Based on all that prior information build such a service."  
 )

# --- Test Runner Function ---
# This function simulates a full chat session for a given agent type.
# Arg 1: "unoptimized" or "optimized"
run_test_session() {
  local agent_type=$1
  local history_file
  local agent_name

  if [ "$agent_type" == "unoptimized" ]; then
    history_file=$UNOPTIMIZED_HISTORY_PATH
    agent_name="Unoptimized Agent"
    echo "[]" > "$history_file" # Initialize empty JSON array
  else
    history_file=$OPTIMIZED_HISTORY_PATH
    agent_name="Optimized Agent"
    # Initialize empty JSON object for optimized history
    echo '{"previous_history_summary": null, "messages": []}' > "$history_file"
  fi

  echo "--- Starting Test: $agent_name ---"

  # Loop through each prompt and simulate a request-response cycle
  for prompt in "${PROMPTS[@]}"; do
    echo
    echo "prompt > $prompt"

    local chat_history
    chat_history=$(cat "$history_file")

    # For the optimized agent, run the summarization script before the request
    if [ "$agent_type" == "optimized" ]; then
      bash "$OPTIMIZE_SCRIPT" "$history_file"
      chat_history=$(cat "$history_file") # Reload history after potential optimization
    fi

    # Construct the full prompt based on the agent type
    local full_prompt
    if [ "$agent_type" == "unoptimized" ]; then
      local history_as_text
      history_as_text=$(echo "$chat_history" | jq -r '.[] | "\(.role): \(.content)"')
      if [ -n "$history_as_text" ]; then
        full_prompt="${history_as_text}"$'
'"user: ${prompt}"
      else
        full_prompt="user: ${prompt}"
      fi
    else # optimized
      local summary
      summary=$(echo "$chat_history" | jq -r '.previous_history_summary // ""')
      local history_as_text
      history_as_text=$(echo "$chat_history" | jq -r '.messages[] | "\(.role): \(.content)"')
      if [ -n "$summary" ] && [ "$summary" != "null" ]; then
        full_prompt="This is a summary of our previous conversation: ${summary}"$'

'
      fi
      if [ -n "$history_as_text" ]; then
        full_prompt="${full_prompt}${history_as_text}"$'
'"user: ${prompt}"
      else
        full_prompt="${full_prompt}user: ${prompt}"
      fi
    fi

    # Make the API call
    local raw_response
    raw_response=$("$REQUEST_GIGACHAT_PATH" "$full_prompt")
    local response_content
    response_content=$(echo "$raw_response" | jq -r '.choices[0].message.content // "Error: Could not parse response."')
    
    echo "response >"
    echo "$response_content"

    # Update the history file
    local usage_stats
    usage_stats=$(echo "$raw_response" | jq '.usage')
    if [ "$agent_type" == "unoptimized" ]; then
      chat_history=$(echo "$chat_history" | jq \
        --arg user_input "$prompt" \
        --arg response_content "$response_content" \
        --argjson usage_stats "$usage_stats" \
        '. += [{"role": "user", "content": $user_input}, {"role": "assistant", "content": $response_content, "usage": $usage_stats}]')
    else # optimized
      chat_history=$(echo "$chat_history" | jq \
        --arg user_input "$prompt" \
        --arg response_content "$response_content" \
        --argjson usage_stats "$usage_stats" \
        '.messages += [{"role": "user", "content": $user_input}, {"role": "assistant", "content": $response_content, "usage": $usage_stats}]')
    fi
    
    echo "$chat_history" > "$history_file"
    
    local total_tokens
    total_tokens=$(echo "$usage_stats" | jq '.total_tokens // 0')
    echo "--- (Tokens for this exchange: $total_tokens) ---"
  done
  echo
  echo "--- $agent_name run complete. ---"
}

# --- Main Execution ---
run_test_session "unoptimized"
echo
run_test_session "optimized"
echo

# --- Data Extraction and Comparison ---
echo "--- Extracting Final Data for Comparison ---"
UNOPTIMIZED_HISTORY_CONTENT=$(cat "$UNOPTIMIZED_HISTORY_PATH")
UNOPTIMIZED_TOTAL_TOKENS=$(echo "$UNOPTIMIZED_HISTORY_CONTENT" | jq '[.[] | select(.role=="assistant") | .usage.total_tokens // 0] | add')

OPTIMIZED_HISTORY_CONTENT=$(cat "$OPTIMIZED_HISTORY_PATH")
OPTIMIZED_TOTAL_TOKENS=$(echo "$OPTIMIZED_HISTORY_CONTENT" | jq '[.messages[] | select(.role=="assistant") | .usage.total_tokens // 0] | add')

echo "Unoptimized Agent Total Tokens: $UNOPTIMIZED_TOTAL_TOKENS"
echo "Optimized Agent Total Tokens: $OPTIMIZED_TOTAL_TOKENS"
echo

# --- Generating Summary ---
echo "--- Generating Comparison Summary via GigaChat ---"
COMPARISON_PROMPT="I have run two versions of a chat agent with the same prompts.
Agent 1 (Unoptimized) sends the entire chat history with every request.
Agent 2 (Optimized) summarizes the history after 3 message pairs.

Analyze the following data and provide a comparison summary. Focus on:
1.  **Token Efficiency**: Compare the total tokens used by each agent.
2.  **Response Quality & Context Retention**: Analyze both agents' final histories to assess if they retained the context.
3.  **Overall Conclusion**: Based on the data, which approach is better? Discuss the trade-offs (e.g., cost vs. context).

--- DATA ---

**Agent 1 (Unoptimized):**
- Total Tokens Used: $UNOPTIMIZED_TOTAL_TOKENS
- Full History JSON:
---
$UNOPTIMIZED_HISTORY_CONTENT
---

**Agent 2 (Optimized):**
- Total Tokens Used: $OPTIMIZED_TOTAL_TOKENS
- Full History JSON (with summary):
---
$OPTIMIZED_HISTORY_CONTENT
---
--- END OF DATA ---

Provide a clear, structured analysis."

SUMMARY_RESPONSE=$("$REQUEST_GIGACHAT_PATH" "$COMPARISON_PROMPT")
SUMMARY_CONTENT=$(echo "$SUMMARY_RESPONSE" | jq -r '.choices[0].message.content // "Error: Could not generate summary."')

echo
echo "--- GigaChat Comparison Analysis ---"
echo "$SUMMARY_CONTENT"
echo "------------------------------------"
