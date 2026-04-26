#!/bin/bash

# Run aichat-c-learning application
# Make sure to set GIGACHAT_API_CREDENTIALS environment variable before running

if [ -z "$GIGACHAT_API_CREDENTIALS" ]; then
    echo "Error: GIGACHAT_API_CREDENTIALS environment variable is not set"
    echo "Please export your GigaChat credentials (Base64-encoded client ID:secret)"
    echo "Example: export GIGACHAT_API_CREDENTIALS='your_base64_credentials'"
    exit 1
fi

# Default mode is CLI
MODE="${1:-cli}"
PORT="${2:-8080}"

if [ "$MODE" = "server" ] || [ "$MODE" = "--server" ]; then
    echo "Starting aichat-c-learning in SERVER mode on port ${PORT}..."
    java -cp target/aichat-c-learning-1.0.jar com.github.pvtitov.aichatclearning.ServerMain "$PORT"
else
    echo "Starting aichat-c-learning in CLI mode..."
    echo "Usage: ./run.sh [server [port]]"
    echo "  cli       - Run in CLI mode (default)"
    echo "  server    - Run as HTTP server (default port 8080)"
    echo "  server 9090 - Run as HTTP server on port 9090"
    echo ""
    java -jar target/aichat-c-learning-1.0.jar
fi
