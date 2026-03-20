#!/bin/bash

# Create a temporary file to store results
TMP_FILE=$(mktemp)
AGENT=/Users/paveltitov/Documents/programming/ai_challenge/gigachat/agent_gigachat.sh
echo "Temporary file created at: $TMP_FILE"

# --- Helper Function to Run and Log ---
run_and_log() {
  local prompt_text="$1"
  local output_file="$2"

  echo "=================================================" | tee -a "$output_file"
  echo "PROMPT:" | tee -a "$output_file"
  echo "$prompt_text" | tee -a "$output_file"
  echo "-------------------------------------------------" | tee -a "$output_file"
  
  echo "RESPONSE:" | tee -a "$output_file"
  # Execute the agent script and append the full output to the temp file and terminal
  $AGENT "$prompt_text" | tee -a "$output_file"
  
  echo | tee -a "$output_file"
}

# --- Prompt 1 ---
PROMPT1="Напиши быструю сортировку на C"
run_and_log "$PROMPT1" "$TMP_FILE"

# --- Prompt 2 ---
PROMPT2="Напиши быструю сортировку на C, решай пошагово"
run_and_log "$PROMPT2" "$TMP_FILE"

# --- Prompt 3 ---
# Generate a prompt by asking the agent to create one
META_PROMPT="Напиши промпт для написания быстрой сортировки на C"
echo "=================================================" | tee -a "$TMP_FILE"
echo "GENERATING PROMPT for run 3..." | tee -a "$TMP_FILE"
echo "Meta-Prompt: $META_PROMPT" | tee -a "$TMP_FILE"
echo "-------------------------------------------------" | tee -a "$TMP_FILE"

# Run the agent, capture the response, and extract the 'full_response' part
GENERATED_PROMPT_RESPONSE=$($AGENT "$META_PROMPT")
echo "$GENERATED_PROMPT_RESPONSE" | tee -a "$TMP_FILE"

# Extract the content between "---FULL RESPONSE:---" and "---SUMMARY:---"
PROMPT3=$(echo "$GENERATED_PROMPT_RESPONSE" | awk '/^---FULL RESPONSE:---$/,/^---SUMMARY:---$/{if (!/^---FULL RESPONSE:---$/ && !/^---SUMMARY:---$/) print}' | sed '/^$/d')

if [ -z "$PROMPT3" ]; then
  echo "Error: Could not generate prompt for the third run. Using a fallback." | tee -a "$TMP_FILE"
  PROMPT3="Напиши реализацию алгоритма быстрой сортировки (Quicksort) на языке программирования C."
fi
run_and_log "$PROMPT3" "$TMP_FILE"


# --- Prompt 4 ---
PROMPT4="Представь, что ты — группа экспертов, состоящая из junior-разработчика и senior-разработчика. Ваша задача — написать код для быстрой сортировки на языке C. Попросите каждого из них представить свое решение и объяснение."
run_and_log "$PROMPT4" "$TMP_FILE"


# --- Comparison ---
echo "================================================="
echo "           SUMMARIZING ALL RESPONSES           "
echo "================================================="
SUMMARIZE_PROMPT="Кратко суммаризируй этот лог с разными реализациями быстрой сортировки. Укажи ключевые особенности каждого из предложенных вариантов кода."
echo "Running summarization..."
SUMMARY_RESPONSE=$($AGENT "$SUMMARIZE_PROMPT" --file "$TMP_FILE")

SUMMARY_TEXT=$(echo "$SUMMARY_RESPONSE" | awk '/^---FULL RESPONSE:---$/,/^---SUMMARY:---$/{if (!/^---FULL RESPONSE:---$/ && !/^---SUMMARY:---$/) print}' | sed '/^$/d')

if [ -z "$SUMMARY_TEXT" ]; then
  echo "Error: Could not generate summary. Aborting final comparison." | tee -a "$TMP_FILE"
  echo "Deleting temporary file: $TMP_FILE"
  rm "$TMP_FILE"
  exit 1
fi

# Create a new temp file for the summary
SUMMARY_FILE=$(mktemp)
echo "$SUMMARY_TEXT" > "$SUMMARY_FILE"


echo "================================================="
echo "           COMPARISON OF ALL RESPONSES           "
echo "================================================="
COMPARISON_PROMPT="Основываясь на содержимом файла, которое является резюме предыдущих ответов, проведи детальный анализ и сравнение ВСЕХ ответов, которые в нем описаны. Проанализируй каждый из следующих пунктов:
1.  Первый ответ (стандартный запрос).
2.  Второй ответ (пошаговый запрос).
3.  Третий ответ (ответ на сгенерированный промпт).
4.  Четвертый ответ (сравнение junior и senior разработчиков).

Для каждого ответа оцени полноту, качество кода и объяснений. Укажи сильные и слабые стороны. Сделай итоговый вывод о том, какой ответ был наиболее удачным."
echo "Running final comparison..."
$AGENT "$COMPARISON_PROMPT" --file "$SUMMARY_FILE"

# --- Cleanup ---
echo "Deleting temporary file: $TMP_FILE"
rm "$TMP_FILE"
echo "Deleting summary file: $SUMMARY_FILE"
rm "$SUMMARY_FILE"
