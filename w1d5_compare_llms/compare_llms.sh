#!/bin/bash

# Check if a prompt is provided
if [ -z "$1" ]; then
  echo "Usage: $0 <prompt>"
  exit 1
fi

PROMPT="$1"
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# --- Agent Paths ---
AGENT_GIGACHAT="$SCRIPT_DIR/../w1d2_gigachat_agent_constraints/agent_gigachat.sh"
AGENT_GEMINI="$SCRIPT_DIR/../w1d1_gemini_agent/agent_gemini.sh"
AGENT_OPENAI="$SCRIPT_DIR/agent_openai.sh"

AGENTS=(
  "GigaChat:$AGENT_GIGACHAT"
  "Gemini:$AGENT_GEMINI"
  "OpenAI:$AGENT_OPENAI"
)

# --- Main Loop ---
for agent_info in "${AGENTS[@]}"; do
  LLM_NAME="${agent_info%%:*}"
  AGENT_PATH="${agent_info#*:}"

  echo "--- LLM: $LLM_NAME ---"

  # Measure execution time
  START_TIME=$(date +%s.%N)
  AGENT_OUTPUT=$($AGENT_PATH "$PROMPT")
  END_TIME=$(date +%s.%N)
  TIME_TAKEN=$(echo "$END_TIME - $START_TIME" | bc)

  # Extract response and tokens
  RESPONSE=$(echo "$AGENT_OUTPUT" | awk '/^---FULL RESPONSE:---$/,/^---$/{if (!/^---FULL RESPONSE:---$/ && !/^---$/) print}' | sed '/^$/d')
  TOKENS=$(echo "$AGENT_OUTPUT" | grep "Tokens spent:" | awk '{print $3}')

  echo "Response:"
  echo "$RESPONSE"
  echo

  # Get quality assessment
  ASSESSMENT_PROMPT="Please assess the quality of the following text: $RESPONSE"
  ASSESSMENT_OUTPUT=$($AGENT_GIGACHAT "$ASSESSMENT_PROMPT")
  ASSESSMENT=$(echo "$ASSESSMENT_OUTPUT" | awk '/^---FULL RESPONSE:---$/,/^---SUMMARY:---$/{if (!/^---FULL RESPONSE:---$/ && !/^---SUMMARY:---$/) print}' | sed '/^$/d')

  echo "Assessment:"
  echo "$ASSESSMENT"
  echo

  echo "Tokens Used: $TOKENS"
  echo "Time Taken: ${TIME_TAKEN}s"
  echo
done
