#!/bin/bash

# Source the file with the API key
source /Users/paveltitov/Documents/programming/ai_challenge/set_api_key.sh

# Check if the GigaChat API credentials are set
if [ -z "$GIGACHAT_API_CREDENTIALS" ]; then
  echo "Error: GIGACHAT_API_CREDENTIALS is not set. Please check set_api_key.sh"
  echo "It should be a Base64 encoded string of 'client_id:client_secret'"
  exit 1
fi

# --- Argument Parsing ---
MAX_TOKENS=""
STOP_SEQUENCE=""
PROMPT_ARGS=""
FILE_PATH=""
TEMPERATURE="0.7" # Default temperature

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

# --- GigaChat Authentication ---
AUTH_URL="https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
RQUID=$(uuidgen)
ACCESS_TOKEN=$(curl -s -X POST "$AUTH_URL" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Accept: application/json" \
  -H "RqUID: $RQUID" \
  -H "Authorization: Basic $GIGACHAT_API_CREDENTIALS" \
  --data-urlencode "scope=GIGACHAT_API_PERS" | jq -r '.access_token')

if [ -z "$ACCESS_TOKEN" ]; then
  echo "Error: Failed to obtain access token."
  exit 1
fi

# The API endpoint for GigaChat
URL="https://gigachat.devices.sberbank.ru/api/v1/chat/completions"

# The user's prompt with instructions for JSON output
BASE_PROMPT="Return ONLY a JSON. Do not include any other text, explanation, or markdown. Each JSON should have the keys 'full_response' and 'summary'. The user's request is: ${PROMPT_ARGS}"

# --- Build JSON Payload ---
# Start with a base payload, letting jq handle the initial JSON structure and escaping
DATA=$(jq -n \
  --arg model "GigaChat:latest" \
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

# Make the API request and print the response, extracting and parsing the JSON
RAW_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $ACCESS_TOKEN" -d "$DATA" "$URL")
# First, try to parse the message content as a JSON string
PARSED_RESPONSE=$(echo "$RAW_RESPONSE" | jq '.choices[0].message.content | fromjson' 2>/dev/null)
TOTAL_TOKENS=$(echo "$RAW_RESPONSE" | jq '.usage.total_tokens')

if [ $? -ne 0 ] || [ -z "$PARSED_RESPONSE" ]; then
  # If fromjson fails or returns empty, treat it as a plain string that might be wrapped in markdown
  CONTENT=$(echo "$RAW_RESPONSE" | jq -r '.choices[0].message.content')

  # Use the python script to robustly extract the JSON object
  SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
  JSON_RESPONSE=$(echo "$CONTENT" | "$SCRIPT_DIR/../shared/extract_json.py")

  # Parse the extracted JSON
  PARSED_RESPONSE=$(echo "$JSON_RESPONSE" | jq '.' 2> /dev/null)

  if [ $? -ne 0 ]; then
    echo "Error: Failed to parse JSON response. Printing raw content:"
    echo "$CONTENT"
    exit 1
  fi
fi

# At this point, PARSED_RESPONSE should hold the valid, parsed JSON object.
FULL_RESPONSE=$(echo "$PARSED_RESPONSE" | jq -r '.full_response')
SUMMARY=$(echo "$PARSED_RESPONSE" | jq -r '.summary')

# Check for empty fields to aid in debugging intermittent API issues
if [ -z "$FULL_RESPONSE" ] && [ -z "$SUMMARY" ]; then
    echo "Error: Extracted full_response and summary are both empty. This might indicate an unusual API response. Raw content processed by extractor:" >&2
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
