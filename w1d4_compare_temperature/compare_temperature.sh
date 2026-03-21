#!/bin/bash

# Check if a prompt is provided
if [ -z "$1" ]; then
  echo "Usage: $0 <prompt>"
  exit 1
fi

PROMPT="$1"
AGENT=/Users/paveltitov/Documents/programming/ai_challenge/w1d2_gigachat_agent_constraints/agent_gigachat.sh

# Create a temporary file to store results
TMP_FILE=$(mktemp)
echo "Temporary file for storing responses: $TMP_FILE"

# --- Run Agent with Different Temperatures ---
TEMPERATURES=(0 0.7 1.2)

for TEMP in "${TEMPERATURES[@]}"; do
  echo "=================================================" | tee -a "$TMP_FILE"
  echo "RUNNING WITH TEMPERATURE: $TEMP" | tee -a "$TMP_FILE"
  echo "=================================================" | tee -a "$TMP_FILE"
  
  # Execute the agent and append its output to the temp file
  $AGENT --temperature "$TEMP" "$PROMPT" | tee -a "$TMP_FILE"
  
  echo | tee -a "$TMP_FILE"
done

# --- Summarize and Compare ---
echo "================================================="
echo "      SUMMARIZING AND COMPARING RESPONSES      "
echo "================================================="

COMPARISON_PROMPT="Основываясь на логе в файле, который содержит три ответа на один и тот же запрос с разными температурами (0, 0.7, 1.2), проведи детальный анализ.

Сравни ответы по следующим критериям:
1.  **Точность (Precision):** Насколько ответ соответствует запросу и не содержит фактических ошибок.
2.  **Креативность (Creativity):** Насколько ответ оригинален, предлагает ли он нестандартные идеи или формулировки.
3.  **Разнообразие (Diversity):** Насколько ответы с разными температурами отличаются друг от друга.

После сравнения, дай свою оценку и рекомендации: для каких типов задач лучше подходит каждая из температур (0, 0.7, 1.2)? Приведи примеры."

echo "Running final analysis..."
# Call the agent with the comparison prompt, using the log file as context
$AGENT "$COMPARISON_PROMPT" --file "$TMP_FILE"

# --- Cleanup ---
echo "Analysis complete. Deleting temporary file: $TMP_FILE"
rm "$TMP_FILE"