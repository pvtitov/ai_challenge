#!/bin/bash

# Source the file with the API key
source set_api_key.sh

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
    *)
      PROMPT_ARGS="$PROMPT_ARGS $1"
      shift
      ;;
  esac
done

# Check if a prompt is provided
if [ -z "$PROMPT_ARGS" ]; then
  echo "Usage: $0 [--max-tokens <number>] [--stop <sequence>] <prompt>"
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
FULL_PROMPT="Return ONLY a JSON list of objects. Do not include any other text, explanation, or markdown. Each object must have the keys 'country', 'name', and 'description'. The user's request is: ${PROMPT_ARGS}"

# --- Build JSON Payload ---
DATA=$(cat << EOF
{
  "model": "GigaChat:latest",
  "messages": [
    {
      "role": "user",
      "content": "$FULL_PROMPT"
    }
  ],
  "temperature": 0.7
}
EOF
)

# Add optional parameters if they are set
if [ -n "$MAX_TOKENS" ]; then
  DATA=$(echo "$DATA" | jq --argjson max_tokens "$MAX_TOKENS" '. + {max_tokens: $max_tokens}')
fi

if [ -n "$STOP_SEQUENCE" ]; then
  DATA=$(echo "$DATA" | jq --arg stop "$STOP_SEQUENCE" '. + {stop: [$stop]}')
fi

echo -e "$DATA\n"

# Make the API request and print the response, extracting and parsing the JSON
RAW_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $ACCESS_TOKEN" -d "$DATA" "$URL")
CONTENT=$(echo "$RAW_RESPONSE" | jq -r '.choices[0].message.content')

# Extract the JSON from the content
JSON_RESPONSE=$(echo "$CONTENT" | sed -n '/\[/,/\]/p')

# If sed fails, try to find json in code blocks
if [ -z "$JSON_RESPONSE" ]; then
    JSON_RESPONSE=$(echo "$CONTENT" | sed -n '/```json/,/```/p' | sed '1d;$d')
fi


# Parse the extracted JSON
PARSED_RESPONSE=$(echo "$JSON_RESPONSE" | jq '.' 2> /dev/null)

if [ $? -ne 0 ]; then
  echo "Error: Failed to parse JSON response. Printing raw content:"
  echo "$CONTENT"
else
  echo "$PARSED_RESPONSE"
fi
