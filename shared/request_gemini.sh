#!/bin/bash

# Source the file with the API key
source /Users/paveltitov/Documents/programming/ai_challenge/set_api_key.sh

# Check if the API key is set
if [ -z "$GEMINI_API_KEY" ]; then
  echo "Error: GEMINI_API_KEY is not set. Please check set_api_key.sh"
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

# The API endpoint for Gemini
URL="https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"

# --- Build JSON Payload ---
DATA=$(jq -n \
  --arg text "${PROMPT_ARGS}" \
  '{contents: [{parts: [{text: $text}]}]}')

# Create generationConfig object
generationConfig=$(jq -n \
  --argjson temp "$TEMPERATURE" \
  '{temperature: $temp}')

# Add optional parameters if they are set
if [ -n "$MAX_TOKENS" ]; then
  generationConfig=$(echo "$generationConfig" | jq --argjson maxOutputTokens "$MAX_TOKENS" '. + {maxOutputTokens: $maxOutputTokens}')
fi

if [ -n "$STOP_SEQUENCE" ]; then
  generationConfig=$(echo "$generationConfig" | jq --arg stop "$STOP_SEQUENCE" '. + {stopSequences: [$stop]}')
fi

DATA=$(echo "$DATA" | jq --argjson config "$generationConfig" '. + {generationConfig: $config}')

# Make the API request and output raw response
curl -s -X POST -H "Content-Type: application/json" -d "$DATA" "$URL"
