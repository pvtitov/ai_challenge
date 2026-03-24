#!/bin/bash

echo "Welcome to the GigaChat Agent!"
echo "Supported commands: /quit, /clean"
echo

# Get the directory where the script is located
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
HISTORY_FILE="$SCRIPT_DIR/chat_history.json"

# Load history if it exists, otherwise initialize
if [ -f "$HISTORY_FILE" ]; then
  CHAT_HISTORY=$(cat "$HISTORY_FILE")
else
  CHAT_HISTORY="[]"
fi

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

  # Update history
  CHAT_HISTORY=$(echo "$CHAT_HISTORY" | jq \
    --arg user_input "$USER_INPUT" \
    --arg response_content "$RESPONSE_CONTENT" \
    '. += [{"role": "user", "content": $user_input}, {"role": "assistant", "content": $response_content}]')

  # Save history to file
  echo "$CHAT_HISTORY" > "$HISTORY_FILE"

done

echo "Goodbye!"