#!/bin/bash

# Source the file with the API key
source /Users/paveltitov/Documents/programming/ai_challenge/set_api_key.sh

# Check if the API key is set
if [ -z "$OPENAI_API_KEY" ]; then
  echo "Error: OPENAI_API_KEY is not set. Please check set_api_key.sh"
  exit 1
fi

# --- Argument Parsing ---
MAX_TOKENS=""
STOP_SEQUENCE=""
PROMPT_ARGS=""
FILE_PATH=""
TEMPERATURE="1" # Default temperature

while [[ $# -gt 0 ]]; do
  case "$1" in
    --max-tokens)
      MAX_TOKENS="$2"
      shift 2
      ;;
    --stop)
      STOP_SEQUENCE="$2"
      shift 2
      ;;
    --file)
      FILE_PATH="$2"
      shift 2
      ;;
    --temperature)
      TEMPERATURE="$2"
      shift 2
      ;;
    *)
      PROMPT_ARGS="$PROMPT_ARGS $1"
      shift
      ;;
  esac
done

# Check if a prompt is provided
if [ -z "$PROMPT_ARGS" ]; then
  echo "Usage: $0 [--max-tokens <number>] [--stop <sequence>] [--temperature <number>] <prompt>"
  exit 1
fi

# The API endpoint for OpenAI
URL="https://api.openai.com/v1/chat/completions"

# The user's prompt with instructions for JSON output
BASE_PROMPT="Return ONLY a JSON. Do not include any other text, explanation, or markdown. Each JSON should have the keys 'full_response' and 'summary'. The user's request is: ${PROMPT_ARGS}"

# --- Build JSON Payload ---
# Start with a base payload
DATA=$(jq -n \
  --arg model "gpt-5-nano-2025-08-07" \
  --arg role "user" \
  --arg content "$BASE_PROMPT" \
  --argjson temp "$TEMPERATURE" \
  '{model: $model, messages: [{role: $role, content: $content}], temperature: $temp}')

# If a file is provided, read its content and prepend it to the 'content' field
if [ -n "$FILE_PATH" ]; then
  if [ -f "$FILE_PATH" ]; then
    FILE_CONTENT=$(cat "$FILE_PATH")
    # Create the full prompt with context
    FULL_PROMPT="Context from file:\n${FILE_CONTENT}\n\n${BASE_PROMPT}"
    # Safely update the 'content' in the JSON payload using jq
    DATA=$(echo "$DATA" | jq --arg new_content "$FULL_PROMPT" '.messages[0].content = $new_content')
  else
    echo "Error: File not found at $FILE_PATH"
    exit 1
  fi
fi

# Add optional parameters if they are set
if [ -n "$MAX_TOKENS" ]; then
  DATA=$(echo "$DATA" | jq --argjson max_tokens "$MAX_TOKENS" '. + {max_tokens: $max_tokens}')
fi

if [ -n "$STOP_SEQUENCE" ]; then
  DATA=$(echo "$DATA" | jq --arg stop "$STOP_SEQUENCE" '. + {stop: [$stop]}')
fi

# Make the API request
RAW_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $OPENAI_API_KEY" -d "$DATA" "$URL")

# Check for API errors
if echo "$RAW_RESPONSE" | jq -e '.error' > /dev/null; then
  echo "Error from OpenAI API:"
  echo "$RAW_RESPONSE" | jq '.'
  exit 1
fi

# Extract content and token count
CONTENT=$(echo "$RAW_RESPONSE" | jq -r '.choices[0].message.content')
TOTAL_TOKENS=$(echo "$RAW_RESPONSE" | jq -r '.usage.total_tokens')

# --- Parse the JSON response from the content ---
PARSED_RESPONSE=$(echo "$CONTENT" | fromjson 2>/dev/null)

if [ $? -ne 0 ] || [ -z "$PARSED_RESPONSE" ]; then
  # If fromjson fails, use the python script to robustly extract the JSON object
  SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
  JSON_RESPONSE=$(echo "$CONTENT" | "$SCRIPT_DIR/../shared/extract_json.py")

  # Parse the extracted JSON
  PARSED_RESPONSE=$(echo "$JSON_RESPONSE" | jq '.' 2> /dev/null)

  if [ $? -ne 0 ]; then
    echo "Error: Failed to parse JSON response from OpenAI. Printing raw content:"
    echo "$CONTENT"
    exit 1
  fi
fi

# At this point, PARSED_RESPONSE should hold the valid, parsed JSON object.
FULL_RESPONSE=$(echo "$PARSED_RESPONSE" | jq -r '.full_response')
SUMMARY=$(echo "$PARSED_RESPONSE" | jq -r '.summary')

# Check for empty fields
if [ -z "$FULL_RESPONSE" ] && [ -z "$SUMMARY" ]; then
    echo "Error: Extracted full_response and summary are both empty from OpenAI. Raw content processed by extractor:" >&2
    echo "$CONTENT" >&2
fi

echo "---FULL RESPONSE:---"
echo "$FULL_RESPONSE"
echo
echo "---SUMMARY:---"
echo "$SUMMARY"
echo
echo "---"
echo "Tokens spent: $TOTAL_TOKENS"
