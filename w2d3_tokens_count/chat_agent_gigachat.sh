#!/bin/bash

echo "Welcome to the GigaChat Agent!"
echo "Supported commands: /quit, /clean"
echo

# Get the directory where the script is located
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
HISTORY_FILE="$SCRIPT_DIR/chat_history.json"

# Setup logging
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/chat_session_$(date +'%Y-%m-%d_%H-%M-%S').log"
echo "Logging session to: $LOG_FILE"
echo


# Load history if it exists, otherwise initialize
if [ -f "$HISTORY_FILE" ]; then
  CHAT_HISTORY=$(cat "$HISTORY_FILE")
else
  CHAT_HISTORY="[]"
fi

# Calculate total tokens from history
TOTAL_HISTORY_TOKENS=$(echo "$CHAT_HISTORY" | jq '[.[] | select(.role=="assistant") | .usage.total_tokens // 0] | add')

while true; do
  # Prompt user for input
  echo -n "prompt > "
  read -r USER_INPUT

  # Check for commands
  if [[ "$USER_INPUT" == "/quit" ]]; then
    break
  fi

  if [[ "$USER_INPUT" == "/clean" ]]; then
    rm -f "$HISTORY_FILE"
    CHAT_HISTORY="[]"
    TOTAL_HISTORY_TOKENS=0
    echo "History has been cleaned."
    continue
  fi

  # Construct the prompt with history
  HISTORY_AS_TEXT=$(echo "$CHAT_HISTORY" | jq -r '.[] | "\(.role): \(.content)"')

  if [ -n "$HISTORY_AS_TEXT" ]; then
    FULL_PROMPT="${HISTORY_AS_TEXT}"$'\n'"user: ${USER_INPUT}"
  else
    FULL_PROMPT="user: ${USER_INPUT}"
  fi

  # Log the request before sending
  {
    echo "--- Request at $(date +'%Y-%m-%d %H:%M:%S') ---"
    echo "$FULL_PROMPT"
    echo
  } >> "$LOG_FILE"

  echo "response >"
  # Call the request script and get the raw response
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

  # Print the response
  echo "$RESPONSE_CONTENT"
  echo

  # Parse usage statistics
  USAGE_STATS=$(echo "$RAW_RESPONSE" | jq '.usage')
  PROMPT_TOKENS=$(echo "$USAGE_STATS" | jq '.prompt_tokens // 0')
  COMPLETION_TOKENS=$(echo "$USAGE_STATS" | jq '.completion_tokens // 0')
  TOTAL_TOKENS=$(echo "$USAGE_STATS" | jq '.total_tokens // 0')

  # Update total history tokens
  if [ -n "$TOTAL_TOKENS" ] && [ "$TOTAL_TOKENS" -gt 0 ]; then
    TOTAL_HISTORY_TOKENS=$((TOTAL_HISTORY_TOKENS + TOTAL_TOKENS))
  fi

  # Print token statistics
  echo "---"
  echo "Tokens count:"
  echo "  - Request: $PROMPT_TOKENS"
  echo "  - Response: $COMPLETION_TOKENS"
  echo "  - Total: $TOTAL_TOKENS"
  echo "Total for chat history: $TOTAL_HISTORY_TOKENS"
  echo "---"
  echo

  # Update history
  CHAT_HISTORY=$(echo "$CHAT_HISTORY" | jq \
    --arg user_input "$USER_INPUT" \
    --arg response_content "$RESPONSE_CONTENT" \
    --argjson usage_stats "$USAGE_STATS" \
    '. += [{"role": "user", "content": $user_input}, {"role": "assistant", "content": $response_content, "usage": $usage_stats}]')

  # Save history to file
  echo "$CHAT_HISTORY" > "$HISTORY_FILE"

done

echo "Goodbye!"
