#!/bin/bash

echo "Welcome to the GigaChat Agent!"
echo "Supported commands: /quit"
echo

# Get the directory where the script is located
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)

while true; do
  # Prompt user for input
  echo -n "prompt > "
  read -r USER_INPUT

  # Check for quit command
  if [[ "$USER_INPUT" == "/quit" ]]; then
    break
  fi

  # Call the request script and get the raw response
  RAW_RESPONSE=$("$SCRIPT_DIR/../shared/request_gigachat.sh" "$USER_INPUT")

  # Check if the request was successful
  if [ $? -ne 0 ]; then
    echo "Error: Failed to get response from GigaChat."
    echo "Details: $RAW_RESPONSE"
    continue
  fi

  # Parse the response to get the message content
  # Use a default value if parsing fails to avoid printing empty lines
  RESPONSE_CONTENT=$(echo "$RAW_RESPONSE" | jq -r '.choices[0].message.content // "Error: Could not parse response."')

  # Print the response
  echo "response > $RESPONSE_CONTENT"
done

echo "Goodbye!"
