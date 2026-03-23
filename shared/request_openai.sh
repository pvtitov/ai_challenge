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

# --- Build JSON Payload ---
DATA=$(jq -n \
  --arg model "gpt-5-nano-2025-08-07" \
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
curl -s -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $OPENAI_API_KEY" -d "$DATA" "$URL"
