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

# The API endpoint for Gemini
URL="https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$GEMINI_API_KEY"

# The user's prompt with instructions for JSON output
BASE_PROMPT="Return ONLY a JSON. Do not include any other text, explanation, or markdown. Each JSON should have the keys 'full_response' and 'summary'. The user's request is: ${PROMPT_ARGS}"

# --- Build JSON Payload ---
# Start with a base payload
DATA=$(jq -n \
  --arg text "$BASE_PROMPT" \
  '{contents: [{parts: [{text: $text}]}]}')

# If a file is provided, read its content and prepend it to the prompt
if [ -n "$FILE_PATH" ]; then
  if [ -f "$FILE_PATH" ]; then
    FILE_CONTENT=$(cat "$FILE_PATH")
    # Create the full prompt with context
    FULL_PROMPT="Context from file:\n${FILE_CONTENT}\n\n${BASE_PROMPT}"
    # Safely update the 'text' in the JSON payload using jq
    DATA=$(echo "$DATA" | jq --arg new_text "$FULL_PROMPT" '.contents[0].parts[0].text = $new_text')
  else
    echo "Error: File not found at $FILE_PATH"
    exit 1
  fi
fi

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

# Make the API request
RAW_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -d "$DATA" "$URL")

# Check for API errors
if echo "$RAW_RESPONSE" | jq -e '.error' > /dev/null; then
  echo "Error from Gemini API:"
  echo "$RAW_RESPONSE" | jq '.'
  exit 1
fi

# Extract content and token count
CONTENT=$(echo "$RAW_RESPONSE" | jq -r '.candidates[0].content.parts[0].text')
TOTAL_TOKENS=$(echo "$RAW_RESPONSE" | jq -r '.usageMetadata.totalTokenCount')

# --- Parse the JSON response from the content ---
PARSED_RESPONSE=$(echo "$CONTENT" | fromjson 2>/dev/null)

if [ $? -ne 0 ] || [ -z "$PARSED_RESPONSE" ]; then
  # If fromjson fails, use the python script to robustly extract the JSON object
  SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
  JSON_RESPONSE=$(echo "$CONTENT" | "$SCRIPT_DIR/../shared/extract_json.py")

  # Parse the extracted JSON
  PARSED_RESPONSE=$(echo "$JSON_RESPONSE" | jq '.' 2> /dev/null)

  if [ $? -ne 0 ]; then
    echo "Error: Failed to parse JSON response from Gemini. Printing raw content:"
    echo "$CONTENT"
    exit 1
  fi
fi

# At this point, PARSED_RESPONSE should hold the valid, parsed JSON object.
FULL_RESPONSE=$(echo "$PARSED_RESPONSE" | jq -r '.full_response')
SUMMARY=$(echo "$PARSED_RESPONSE" | jq -r '.summary')

# Check for empty fields
if [ -z "$FULL_RESPONSE" ] && [ -z "$SUMMARY" ]; then
    echo "Error: Extracted full_response and summary are both empty from Gemini. Raw content processed by extractor:" >&2
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