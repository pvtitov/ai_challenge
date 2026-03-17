#!/bin/bash

# Source the file with the API key
source set_api_key.sh

# Check if the API key is set
if [ -z "$GEMINI_API_KEY" ]; then
  echo "Error: GEMINI_API_KEY is not set. Please check set_api_key.sh"
  exit 1
fi

# Check if a prompt is provided
if [ -z "$1" ]; then
  echo "Usage: $0 <prompt>"
  exit 1
fi

# The API endpoint for gemini-2.5-flash
URL="https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"

# The prompt from the first argument.
PROMPT=$1

# The data to send in the request
DATA=$(cat << EOF
{
  "contents": [{
    "parts": [{
      "text": "$PROMPT"
    }]
  }]
}
EOF
)

# Make the API request and print the response, extracting only the text
curl -s -X POST -H "Content-Type: application/json" -d "$DATA" "$URL" | jq -r '.candidates[0].content.parts[0].text'
