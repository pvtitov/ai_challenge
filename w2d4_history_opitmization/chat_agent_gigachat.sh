#!/bin/bash

echo "Welcome to the GigaChat Agent!"
echo "Supported commands: /quit, /clean"
echo

# Get the directory where the script is located
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
HISTORY_FILE="$SCRIPT_DIR/chat_history.json"
OPTIMIZE_SCRIPT="$SCRIPT_DIR/optimize_history.sh"

# Setup logging
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/chat_session_$(date +'%Y-%m-%d_%H-%M-%S').log"
echo "Logging session to: $LOG_FILE"
echo

# Function to initialize or load history
load_history() {
  if [ -f "$HISTORY_FILE" ]; then
    # Check for old format and convert if necessary
    if [[ $(jq 'type' "$HISTORY_FILE") == "\"array\"" ]]; then
      echo "Old history format detected. Converting..."
      CHAT_HISTORY=$(jq -n --argjson msgs "$(cat "$HISTORY_FILE")" '{previous_history_summary: null, messages: $msgs}')
      echo "$CHAT_HISTORY" > "$HISTORY_FILE"
    else
      CHAT_HISTORY=$(cat "$HISTORY_FILE")
    fi
  else
    # Initialize a new history file with the correct structure
    CHAT_HISTORY='{"previous_history_summary": null, "messages": []}'
    echo "$CHAT_HISTORY" > "$HISTORY_FILE"
  fi
}

# Function to calculate total tokens from the assistant's usage stats
calculate_tokens() {
    echo "$CHAT_HISTORY" | jq '[.messages[] | select(.role=="assistant") | .usage.total_tokens // 0] | add'
}

# Initial load of history
load_history
TOTAL_HISTORY_TOKENS=$(calculate_tokens)

while true; do
  # Run the optimization script before prompting the user
  bash "$OPTIMIZE_SCRIPT" "$HISTORY_FILE"
  # Reload history in case it was modified by the optimizer
  load_history
  TOTAL_HISTORY_TOKENS=$(calculate_tokens)

  # Prompt user for input
  echo -n "prompt > "
  read -r USER_INPUT

  # Check for commands
  if [[ "$USER_INPUT" == "/quit" ]]; then
    break
  fi

  if [[ "$USER_INPUT" == "/clean" ]]; then
    # Reset the history file to its initial state
    CHAT_HISTORY='{"previous_history_summary": null, "messages": []}'
    echo "$CHAT_HISTORY" > "$HISTORY_FILE"
    TOTAL_HISTORY_TOKENS=0
    echo "History has been cleaned."
    continue
  fi

  # Construct the prompt, including the summary and recent messages
  SUMMARY=$(echo "$CHAT_HISTORY" | jq -r '.previous_history_summary // ""')
  HISTORY_AS_TEXT=$(echo "$CHAT_HISTORY" | jq -r '.messages[] | "\(.role): \(.content)"')

  FULL_PROMPT=""
  # Prepend the summary if it exists
  if [ -n "$SUMMARY" ] && [ "$SUMMARY" != "null" ]; then
    FULL_PROMPT="This is a summary of our previous conversation: ${SUMMARY}"$'

'
  fi

  # Append the recent conversation history and the new user input
  if [ -n "$HISTORY_AS_TEXT" ]; then
    FULL_PROMPT="${FULL_PROMPT}${HISTORY_AS_TEXT}"$'
'"user: ${USER_INPUT}"
  else
    FULL_PROMPT="${FULL_PROMPT}user: ${USER_INPUT}"
  fi

  # Log the full request prompt
  {
    echo "--- Request at $(date +'%Y-%m-%d %H:%M:%S') ---"
    echo "$FULL_PROMPT"
    echo
  } >> "$LOG_FILE"

  echo "response >"
  # Call the request script and get the raw API response
  RAW_RESPONSE=$("$SCRIPT_DIR/../shared/request_gigachat.sh" "$FULL_PROMPT")

  # Check if the request was successful
  if [ $? -ne 0 ]; then
    echo "Error: Failed to get response from GigaChat."
    echo "Details: $RAW_RESPONSE"
    continue
  fi

  # Parse the response to get the message content
  RESPONSE_CONTENT=$(echo "$RAW_RESPONSE" | jq -r '.choices[0].message.content // "Error: Could not parse response."')

  # Check for parsing errors
  if [[ "$RESPONSE_CONTENT" == "Error: Could not parse response." ]]; then
      echo "$RESPONSE_CONTENT"
      echo "Raw response: $RAW_RESPONSE"
      continue
  fi

  # Print the response to the user
  echo "$RESPONSE_CONTENT"
  echo

  # Parse usage statistics from the response
  USAGE_STATS=$(echo "$RAW_RESPONSE" | jq '.usage')
  PROMPT_TOKENS=$(echo "$USAGE_STATS" | jq '.prompt_tokens // 0')
  COMPLETION_TOKENS=$(echo "$USAGE_STATS" | jq '.completion_tokens // 0')
  TOTAL_TOKENS=$(echo "$USAGE_STATS" | jq '.total_tokens // 0')

  # Update total history tokens
  if [ -n "$TOTAL_TOKENS" ] && [ "$TOTAL_TOKENS" -gt 0 ]; then
    TOTAL_HISTORY_TOKENS=$((TOTAL_HISTORY_TOKENS + TOTAL_TOKENS))
  fi

  # Print token statistics for the current exchange
  echo "---"
  echo "Tokens count:"
  echo "  - Request: $PROMPT_TOKENS"
  echo "  - Response: $COMPLETION_TOKENS"
  echo "  - Total: $TOTAL_TOKENS"
  echo "Total for chat history: $TOTAL_HISTORY_TOKENS"
  echo "---"
  echo

  # Update the history object with the new user/assistant pair
  CHAT_HISTORY=$(echo "$CHAT_HISTORY" | jq \
    --arg user_input "$USER_INPUT" \
    --arg response_content "$RESPONSE_CONTENT" \
    --argjson usage_stats "$USAGE_STATS" \
    '.messages += [{"role": "user", "content": $user_input}, {"role": "assistant", "content": $response_content, "usage": $usage_stats}]')

  # Save the updated history to the file
  echo "$CHAT_HISTORY" > "$HISTORY_FILE"

done

echo "Goodbye!"