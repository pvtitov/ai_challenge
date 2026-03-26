#!/bin/bash

# This script optimizes the chat history file by summarizing old entries.
# It is designed to be called by the main chat agent script.

HISTORY_FILE="$1"

# Exit if no history file is provided or if it doesn't exist.
if [ -z "$HISTORY_FILE" ] || [ ! -f "$HISTORY_FILE" ]; then
  exit 0
fi

# Ensure jq is installed, as it's essential for JSON manipulation.
if ! command -v jq &> /dev/null; then
    echo "Error: jq is not installed. Please install it to continue." >&2
    exit 1
fi

# Read the entire history content from the file.
HISTORY_CONTENT=$(cat "$HISTORY_FILE")

# If history is in the old array format, convert it to the new object format.
if [[ $(echo "$HISTORY_CONTENT" | jq 'type') == "\"array\"" ]]; then
  echo "Old history format detected. Converting to new format."
  HISTORY_CONTENT=$(jq -n --argjson messages "$HISTORY_CONTENT" '{previous_history_summary: null, messages: $messages}')
  echo "$HISTORY_CONTENT" > "$HISTORY_FILE"
fi

# Get the number of messages in the history.
NUM_MESSAGES=$(echo "$HISTORY_CONTENT" | jq '.messages | length')

# Summarization is triggered only if there are more than 3 message pairs (6 messages).
if [ "$NUM_MESSAGES" -le 6 ]; then
  exit 0
fi

echo "History has more than 3 pairs of messages. Optimizing..."

# Determine the number of messages to be summarized (all except the last 3 pairs).
NUM_TO_SUMMARIZE=$((NUM_MESSAGES - 6))

# Extract the messages that need to be summarized.
MESSAGES_TO_SUMMARIZE=$(echo "$HISTORY_CONTENT" | jq ".messages | .[0:$NUM_TO_SUMMARIZE]")
# Extract the recent messages that will be kept.
RECENT_MESSAGES=$(echo "$HISTORY_CONTENT" | jq ".messages | .[$NUM_TO_SUMMARIZE:]")

# Get the summary from the previous optimization, if it exists.
PREVIOUS_SUMMARY=$(echo "$HISTORY_CONTENT" | jq -r '.previous_history_summary // ""')

# Format the messages to be summarized into a clean text block for the prompt.
SUMMARY_PROMPT_TEXT=$(echo "$MESSAGES_TO_SUMMARIZE" | jq -r '.[] | "\(.role): \(.content)"')

# Construct the final prompt for the summarization request.
if [ -n "$PREVIOUS_SUMMARY" ] && [ "$PREVIOUS_SUMMARY" != "null" ]; then
  PROMPT_FOR_GIGA="Summarize the following conversation, building upon the previous summary.\n\nPrevious summary:\n'${PREVIOUS_SUMMARY}'\n\nNew conversation to add to summary:\n${SUMMARY_PROMPT_TEXT}"
else
  PROMPT_FOR_GIGA="Summarize the following conversation:\n\n${SUMMARY_PROMPT_TEXT}"
fi

# Get the absolute path to the shared request script.
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
REQUEST_SCRIPT="$SCRIPT_DIR/../shared/request_gigachat.sh"

# Call the GigaChat API to generate a new summary.
NEW_SUMMARY_RAW=$("$REQUEST_SCRIPT" "$PROMPT_FOR_GIGA")

# Check for errors during the API call.
if [ $? -ne 0 ]; then
  echo "Error: Failed to get summary from GigaChat." >&2
  echo "Details: $NEW_SUMMARY_RAW" >&2
  exit 1
fi

# Extract the new summary content from the API response.
NEW_SUMMARY=$(echo "$NEW_SUMMARY_RAW" | jq -r '.choices[0].message.content // "Failed to generate summary."')

# Create the new, optimized history object.
NEW_HISTORY=$(jq -n \
  --arg new_summary "$NEW_SUMMARY" \
  --argjson recent_messages "$RECENT_MESSAGES" \
  '{previous_history_summary: $new_summary, messages: $recent_messages}')

# Overwrite the old history file with the new, optimized version.
echo "$NEW_HISTORY" > "$HISTORY_FILE"

echo "History optimization complete."
