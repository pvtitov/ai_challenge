#!/bin/bash

# Run aichat-c-learning application
# Make sure to set GIGACHAT_API_CREDENTIALS environment variable before running

if [ -z "$GIGACHAT_API_CREDENTIALS" ]; then
    echo "Error: GIGACHAT_API_CREDENTIALS environment variable is not set"
    echo "Please export your GigaChat credentials (Base64-encoded client ID:secret)"
    echo "Example: export GIGACHAT_API_CREDENTIALS='your_base64_credentials'"
    exit 1
fi

echo "Starting aichat-c-learning..."
java -jar target/aichat-c-learning-1.0.jar
