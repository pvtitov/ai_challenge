#!/bin/bash

# Source the file with the API key
source /Users/paveltitov/Documents/programming/ai_challenge/set_api_key.sh

# Check if the GigaChat API credentials are set
if [ -z "$GIGACHAT_API_CREDENTIALS" ]; then
  echo "Error: GIGACHAT_API_CREDENTIALS is not set. Please check set_api_key.sh"
  exit 1
fi

# --- Argument Parsing ---
MAX_TOKENS=""
STOP_SEQUENCE=""
PROMPT_ARGS=""
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

# --- Build JSON Payload ---
DATA=$(jq -n \
  --arg model "GigaChat:latest" \
  --arg role "user" \
  --arg content "${PROMPT_ARGS}" \
  --argjson temp "$TEMPERATURE" \
  '{model: $model, messages: [{role: $role, content: $content}], temperature: $temp}')

# Add optional parameters if they are set
if [ -n "$MAX_TOKENS" ]; then
  DATA=$(echo "$DATA" | jq --argjson max_tokens "$MAX_TOKENS" '. + {max_tokens: $max_tokens}')
fi

if [ -n "$STOP_SEQUENCE" ]; then
  DATA=$(echo "$DATA" | jq --arg stop "$STOP_SEQUENCE" '. + {stop: [$stop]}')
fi

# Make the API request and output raw response
curl -s -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $ACCESS_TOKEN" -d "$DATA" "$URL"
