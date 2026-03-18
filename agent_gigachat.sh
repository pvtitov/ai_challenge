#!/bin/bash

# Source the file with the API key
source set_api_key.sh

# Check if the GigaChat API credentials are set
if [ -z "$GIGACHAT_API_CREDENTIALS" ]; then
  echo "Error: GIGACHAT_API_CREDENTIALS is not set. Please check set_api_key.sh"
  echo "It should be a Base64 encoded string of 'client_id:client_secret'"
  exit 1
fi

# Check if a prompt is provided
if [ -z "$1" ]; then
  echo "Usage: $0 <prompt>"
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

# The prompt from the first argument.
PROMPT=$1

# The data to send in the request
DATA=$(cat << EOF
{
  "model": "GigaChat:latest",
  "messages": [
    {
      "role": "user",
      "content": "$PROMPT"
    }
  ],
  "temperature": 0.7
}
EOF
)

# Make the API request and print the response, extracting only the text
RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $ACCESS_TOKEN" -d "$DATA" "$URL" | jq -r '.choices[0].message.content')
echo -e "\nThe answer is:\n\n$RESPONSE"
