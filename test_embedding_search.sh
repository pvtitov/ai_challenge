#!/bin/bash

# Test script to compare LLM answers without and with embeddings

set -e

AICHAT_URL="http://localhost:8080"
EMBEDDING_TOOL_JAR="embedding-tool/target/embedding-tool-1.0.jar"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

QUESTIONS=(
    "How to learn C programming language?"
    "Is it better to use IDE or not?"
    "What is undefined behavior?"
    "What problems are there in C with strings?"
    "Duff's device"
    "How to debug C programm?"
    "Libraries in C"
    "Helloworld in C"
    "Testing C programm"
    "Write quick sorting in C"
)

TMP_DIR=$(mktemp -d)
SUMMARIES_BEFORE="$TMP_DIR/summaries_before.txt"
SUMMARIES_AFTER="$TMP_DIR/summaries_after.txt"
EMBEDDINGS_DB="$SCRIPT_DIR/embeddings.db"

cleanup() {
    echo ""
    echo "=== Cleaning up temporary files ==="
    rm -rf "$TMP_DIR"
    echo "Removed: $TMP_DIR"
    echo "Cleanup complete."
}

trap cleanup EXIT

send_question() {
    local question="$1"
    local output_file="$2"
    
    echo "  Question: $question"
    
    # Use curl to save response to temp file, then parse with python3
    local raw_file="$TMP_DIR/raw_response_$$.json"
    # Note: curl may exit with code 18 (transfer closed early) but data is still saved
    curl -s -X POST "$AICHAT_URL/chat" \
        -H "Content-Type: application/json" \
        -d "{\"prompt\": \"$question\"}" \
        --max-time 120 \
        -o "$raw_file" 2>/dev/null || true
    
    # Parse the first JSON object from the response
    response_text=$(python3 << PYEOF
import json, sys

with open('$raw_file', 'r') as f:
    raw = f.read()

# Find the first complete JSON object
depth = 0
start = None
for i, c in enumerate(raw):
    if c == '{':
        if depth == 0:
            start = i
        depth += 1
    elif c == '}':
        depth -= 1
        if depth == 0 and start is not None:
            data = json.loads(raw[start:i+1])
            text = data.get('response') or data.get('fullResponse') or data.get('plan') or str(data)
            print(text)
            sys.exit(0)

print('[No response parsed]', file=sys.stderr)
sys.exit(1)
PYEOF
    )
    
    rm -f "$raw_file"
    
    if [ -z "$response_text" ]; then
        echo "  ✗ Failed to parse response"
        echo "[Failed to get response]" >> "$output_file"
        return
    fi
    
    echo "$response_text" >> "$output_file"
    local bytes=$(echo "$response_text" | wc -c | tr -d ' ')
    echo "  ✓ Answer saved ($bytes bytes)"
    echo ""
}

extract_summary() {
    local answer_file="$1"
    local summary_file="$2"
    
    # Create a summary: first 400 chars
    head -c 400 "$answer_file" > "$summary_file"
    if [ "$(wc -c < "$answer_file")" -gt 400 ]; then
        echo -n "..." >> "$summary_file"
    fi
}

echo "================================================================"
echo "  EMBEDDING SEARCH COMPARISON TEST"
echo "================================================================"
echo ""

# Check if aichat server is running
echo "=== Checking if aichat server is running ==="
if ! curl -s "$AICHAT_URL" --max-time 3 > /dev/null 2>&1; then
    echo "ERROR: aichat server is not running at $AICHAT_URL"
    echo "Please start it first with: ./run_servers.sh"
    exit 1
fi
echo "✓ aichat server is running"
echo ""

# Check if Ollama is running
echo "=== Checking if Ollama is running ==="
if ! curl -s "http://localhost:11434/api/tags" --max-time 3 > /dev/null 2>&1; then
    echo "ERROR: Ollama is not running at http://localhost:11434"
    echo "Please start Ollama first"
    exit 1
fi
echo "✓ Ollama is running"
echo ""

# Step 1: Clean up embeddings database
echo "================================================================"
echo "  STEP 1: Cleaning up embeddings database"
echo "================================================================"
if [ -f "$EMBEDDINGS_DB" ]; then
    rm -f "$EMBEDDINGS_DB"
    rm -f "${EMBEDDINGS_DB}-shm"
    rm -f "${EMBEDDINGS_DB}-wal"
    echo "✓ Removed existing embeddings database"
else
    echo "✓ No existing embeddings database found"
fi
echo ""

# Step 2: Run questions WITHOUT embeddings
echo "================================================================"
echo "  STEP 2: Running 10 questions WITHOUT embeddings"
echo "================================================================"
> "$SUMMARIES_BEFORE"

for i in "${!QUESTIONS[@]}"; do
    question="${QUESTIONS[$i]}"
    echo ""
    echo "--- Question $((i+1))/10 ---"
    
    answer_file="$TMP_DIR/answer_before_$i.txt"
    summary_file="$TMP_DIR/summary_before_$i.txt"
    
    send_question "$question" "$answer_file"
    extract_summary "$answer_file" "$summary_file"
    
    echo "Q$((i+1)): $question" >> "$SUMMARIES_BEFORE"
    echo "Answer summary:" >> "$SUMMARIES_BEFORE"
    cat "$summary_file" >> "$SUMMARIES_BEFORE"
    echo "" >> "$SUMMARIES_BEFORE"
    echo "---" >> "$SUMMARIES_BEFORE"
    echo "" >> "$SUMMARIES_BEFORE"
done

echo ""
echo "✓ All 10 questions answered (without embeddings)"
echo ""

# Step 3: Run embedding tool on learn_c_the_hard_way.pdf
echo "================================================================"
echo "  STEP 3: Creating embeddings for learn_c_the_hard_way.pdf"
echo "================================================================"

if [ ! -f "$SCRIPT_DIR/learn_c_the_hard_way.pdf" ]; then
    echo "ERROR: learn_c_the_hard_way.pdf not found in $SCRIPT_DIR"
    exit 1
fi

if [ ! -f "$EMBEDDING_TOOL_JAR" ]; then
    echo "Building embedding-tool..."
    cd "$SCRIPT_DIR/embedding-tool"
    mvn clean package -DskipTests -q
    cd "$SCRIPT_DIR"
fi

echo "Running embedding-tool on learn_c_the_hard_way.pdf..."
cd "$SCRIPT_DIR"
java -jar "$EMBEDDING_TOOL_JAR" learn_c_the_hard_way.pdf --chunk 500 --overlap 50

if [ -f "$EMBEDDINGS_DB" ]; then
    echo "✓ Embeddings created successfully"
    # Show count of embeddings
    count=$(sqlite3 "$EMBEDDINGS_DB" "SELECT COUNT(*) FROM embedding_index;" 2>/dev/null || echo "unknown")
    echo "  Total chunks in database: $count"
else
    echo "ERROR: Embeddings database not created"
    exit 1
fi
echo ""

# Step 4: Run questions WITH embeddings
echo "================================================================"
echo "  STEP 4: Running 10 questions WITH embeddings"
echo "================================================================"
> "$SUMMARIES_AFTER"

for i in "${!QUESTIONS[@]}"; do
    question="${QUESTIONS[$i]}"
    echo ""
    echo "--- Question $((i+1))/10 ---"
    
    answer_file="$TMP_DIR/answer_after_$i.txt"
    summary_file="$TMP_DIR/summary_after_$i.txt"
    
    send_question "$question" "$answer_file"
    extract_summary "$answer_file" "$summary_file"
    
    echo "Q$((i+1)): $question" >> "$SUMMARIES_AFTER"
    echo "Answer summary:" >> "$SUMMARIES_AFTER"
    cat "$summary_file" >> "$SUMMARIES_AFTER"
    echo "" >> "$SUMMARIES_AFTER"
    echo "---" >> "$SUMMARIES_AFTER"
    echo "" >> "$SUMMARIES_AFTER"
done

echo ""
echo "✓ All 10 questions answered (with embeddings)"
echo ""

# Step 5: Print comparison
echo "================================================================"
echo "  COMPARISON RESULTS"
echo "================================================================"
echo ""
echo "================================================================"
echo "  ANSWERS WITHOUT EMBEDDINGS (10 questions)"
echo "================================================================"
cat "$SUMMARIES_BEFORE"
echo ""

echo "================================================================"
echo "  ANSWERS WITH EMBEDDINGS (10 questions)"
echo "================================================================"
cat "$SUMMARIES_AFTER"
echo ""

echo "================================================================"
echo "  TEST COMPLETE"
echo "================================================================"
echo ""
echo "Both sets of 10 questions have been answered."
echo "Summaries printed above for comparison."
echo "Temporary files will be cleaned up automatically."
