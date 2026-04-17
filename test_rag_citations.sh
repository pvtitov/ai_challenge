#!/bin/bash

# Test script to validate enhanced RAG with sources, citations, and "don't know" mode

set -e

AICHAT_URL="http://localhost:8080"
EMBEDDING_TOOL_JAR="embedding-tool/target/embedding-tool-1.0.jar"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Mix of questions that should have good matches and ones that should trigger "don't know" mode
QUESTIONS=(
    "How to learn C programming language?"
    "What is undefined behavior in C?"
    "What problems are there in C with strings?"
    "Explain Duff's device in C"
    "How to use libraries in C?"
    "What is the capital of Mars?"
    "How to debug C programs?"
    "What are the best practices for C memory management?"
    "Tell me about quantum entanglement in C programming"
    "Write hello world in C"
)

TMP_DIR=$(mktemp -d)
RESULTS_FILE="$TMP_DIR/test_results.txt"
EMBEDDINGS_DB="$SCRIPT_DIR/embeddings.db"

# Counters
TOTAL_QUESTIONS=10
QUESTIONS_WITH_SOURCES=0
QUESTIONS_WITH_CITATIONS=0
QUESTIONS_WITH_DONT_KNOW=0
MEANING_MATCHES=0

cleanup() {
    echo ""
    echo "=== Cleaning up temporary files ==="
    rm -rf "$TMP_DIR"
    echo "Removed: $TMP_DIR"
    echo "Cleanup complete."
}

trap cleanup EXIT

# Send a question and complete the full 3-stage conversation flow
# Stage 1: Send question -> get PLAN response
# Stage 2: Send "y" -> get ACTION_RESULT
# Stage 3: Send "y" -> get FINAL response with sources/citations
send_question() {
    local question="$1"
    local output_file="$2"
    local stage_file="$3"
    local cookie_jar="$TMP_DIR/cookies_question.txt"

    echo "  Question: $question"

    # Stage 1: Send question
    echo "  → Stage 1: Getting plan..."
    local raw_file="$TMP_DIR/raw_stage1.json"
    curl -s -X POST "$AICHAT_URL/chat" \
        -H "Content-Type: application/json" \
        -d "{\"prompt\": \"$question\"}" \
        --max-time 180 \
        -c "$cookie_jar" \
        -o "$raw_file" 2>/dev/null || true

    # Check if file has content
    if [ ! -s "$raw_file" ]; then
        echo "  ✗ Stage 1 response is empty"
        echo "[Failed to get response]" > "$output_file"
        echo "low_relevance=false" > "$stage_file"
        echo "response_type=ERROR" >> "$stage_file"
        return
    fi

    # Parse Stage 1 response using Python
    python3 - "$raw_file" << 'PYEOF' > "$TMP_DIR/stage1_parsed.json" 2>/dev/null
import json, sys

raw_file = sys.argv[1]
with open(raw_file, 'r') as f:
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
            print(json.dumps(data))
            sys.exit(0)

sys.exit(1)
PYEOF

    if [ ! -s "$TMP_DIR/stage1_parsed.json" ]; then
        echo "  ✗ Failed to parse Stage 1 response"
        echo "  Raw content (first 200 chars):"
        head -c 200 "$raw_file"
        echo ""
        echo "[Failed to parse response]" > "$output_file"
        echo "low_relevance=false" > "$stage_file"
        echo "response_type=ERROR" >> "$stage_file"
        return
    fi

    rm -f "$raw_file"

    local requires_confirmation
    requires_confirmation=$(python3 -c "
import json
with open('$TMP_DIR/stage1_parsed.json') as f:
    d = json.load(f)
print(str(d.get('requiresConfirmation', False)).lower())
" 2>/dev/null || echo "false")

    local response_type
    response_type=$(python3 -c "
import json
with open('$TMP_DIR/stage1_parsed.json') as f:
    d = json.load(f)
print(d.get('responseType', 'FINAL'))
" 2>/dev/null || echo "FINAL")

    local low_relevance
    low_relevance=$(python3 -c "
import json
with open('$TMP_DIR/stage1_parsed.json') as f:
    d = json.load(f)
print(str(d.get('lowRelevance', False)).lower())
" 2>/dev/null || echo "false")

    echo "  → Stage 1 complete (responseType: $response_type, requiresConfirmation: $requires_confirmation, lowRelevance: $low_relevance)"

    # Stage 2: Approve plan (send "y")
    if [ "$requires_confirmation" = "true" ]; then
        echo "  → Stage 2: Approving plan..."
        local raw_file2="$TMP_DIR/raw_stage2.json"
        curl -s -X POST "$AICHAT_URL/chat" \
            -H "Content-Type: application/json" \
            -d '{"prompt": "y"}' \
            --max-time 180 \
            -b "$cookie_jar" -c "$cookie_jar" \
            -o "$raw_file2" 2>/dev/null || true

        # Check if file has content
        if [ ! -s "$raw_file2" ]; then
            echo "  ✗ Stage 2 response is empty"
            echo "[Failed to get response]" > "$output_file"
            echo "low_relevance=$low_relevance" > "$stage_file"
            echo "response_type=ERROR" >> "$stage_file"
            return
        fi

        # Parse Stage 2 response
        python3 - "$raw_file2" << 'PYEOF' > "$TMP_DIR/stage2_parsed.json" 2>/dev/null
import json, sys

raw_file = sys.argv[1]
with open(raw_file, 'r') as f:
    raw = f.read()

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
            print(json.dumps(data))
            sys.exit(0)

sys.exit(1)
PYEOF

        if [ ! -s "$TMP_DIR/stage2_parsed.json" ]; then
            echo "  ✗ Failed to parse Stage 2 response"
            echo "  Raw content (first 200 chars):"
            head -c 200 "$raw_file2"
            echo ""
            echo "[Failed to parse response]" > "$output_file"
            echo "low_relevance=$low_relevance" > "$stage_file"
            echo "response_type=ERROR" >> "$stage_file"
            return
        fi

        rm -f "$raw_file2"

        requires_confirmation=$(python3 -c "
import json
with open('$TMP_DIR/stage2_parsed.json') as f:
    d = json.load(f)
print(str(d.get('requiresConfirmation', False)).lower())
" 2>/dev/null || echo "false")

        response_type=$(python3 -c "
import json
with open('$TMP_DIR/stage2_parsed.json') as f:
    d = json.load(f)
print(d.get('responseType', 'FINAL'))
" 2>/dev/null || echo "FINAL")

        echo "  → Stage 2 complete (responseType: $response_type, requiresConfirmation: $requires_confirmation)"

        # Stage 3: Approve action (send "y")
        if [ "$requires_confirmation" = "true" ]; then
            echo "  → Stage 3: Approving action..."
            local raw_file3="$TMP_DIR/raw_stage3.json"
            curl -s -X POST "$AICHAT_URL/chat" \
                -H "Content-Type: application/json" \
                -d '{"prompt": "y"}' \
                --max-time 180 \
                -b "$cookie_jar" -c "$cookie_jar" \
                -o "$raw_file3" 2>/dev/null || true

            # Check if file has content
            if [ ! -s "$raw_file3" ]; then
                echo "  ✗ Stage 3 response is empty"
                echo "[Failed to get response]" > "$output_file"
                echo "low_relevance=$low_relevance" > "$stage_file"
                echo "response_type=ERROR" >> "$stage_file"
                return
            fi

            # Parse Stage 3 response
            python3 - "$raw_file3" << 'PYEOF' > "$TMP_DIR/stage3_parsed.json" 2>/dev/null
import json, sys

raw_file = sys.argv[1]
with open(raw_file, 'r') as f:
    raw = f.read()

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
            print(json.dumps(data))
            sys.exit(0)

sys.exit(1)
PYEOF

            if [ ! -s "$TMP_DIR/stage3_parsed.json" ]; then
                echo "  ✗ Failed to parse Stage 3 response"
                echo "  Raw content (first 200 chars):"
                head -c 200 "$raw_file3"
                echo ""
                echo "[Failed to parse response]" > "$output_file"
                echo "low_relevance=$low_relevance" > "$stage_file"
                echo "response_type=ERROR" >> "$stage_file"
                return
            fi

            rm -f "$raw_file3"

            response_type=$(python3 -c "
import json
with open('$TMP_DIR/stage3_parsed.json') as f:
    d = json.load(f)
print(d.get('responseType', 'FINAL'))
" 2>/dev/null || echo "FINAL")

            echo "  → Stage 3 complete (responseType: $response_type)"

            # Extract final response text with sources/citations appended
            python3 - "$TMP_DIR/stage3_parsed.json" > "$output_file" << 'PYEOF'
import json, sys

parsed_file = sys.argv[1]
with open(parsed_file) as f:
    d = json.load(f)
text = d.get('fullResponse') or d.get('response') or d.get('plan') or str(d)
print(text)
PYEOF
        else
            # Stage 2 didn't require confirmation, use Stage 2 response
            echo "  → Stage 2 didn't require confirmation, using Stage 2 response"
            python3 - "$TMP_DIR/stage2_parsed.json" > "$output_file" << 'PYEOF'
import json, sys

parsed_file = sys.argv[1]
with open(parsed_file) as f:
    d = json.load(f)
text = d.get('fullResponse') or d.get('response') or d.get('plan') or str(d)
print(text)
PYEOF
        fi
    else
        # Stage 1 didn't require confirmation, use Stage 1 response
        echo "  → Stage 1 didn't require confirmation, using Stage 1 response"
        python3 - "$TMP_DIR/stage1_parsed.json" > "$output_file" << 'PYEOF'
import json, sys

parsed_file = sys.argv[1]
with open(parsed_file) as f:
    d = json.load(f)
text = d.get('fullResponse') or d.get('response') or d.get('plan') or str(d)
print(text)
PYEOF
    fi

    # Clean up parsed files
    rm -f "$TMP_DIR/stage1_parsed.json" "$TMP_DIR/stage2_parsed.json" "$TMP_DIR/stage3_parsed.json"

    # Save stage info for analysis
    echo "low_relevance=$low_relevance" > "$stage_file"
    echo "response_type=$response_type" >> "$stage_file"

    rm -f "$cookie_jar"

    if [ -f "$output_file" ] && [ -s "$output_file" ]; then
        local bytes=$(wc -c < "$output_file" | tr -d ' ')
        echo "  ✓ Final answer saved ($bytes bytes)"
    else
        echo "  ✗ Failed to save response"
        echo "[Failed to get response]" > "$output_file"
    fi
    echo ""
}

check_has_sources() {
    local response_file="$1"
    
    # Check for "Sources:" or "**Sources:**" pattern in response
    if grep -qi "sources:" "$response_file" 2>/dev/null; then
        return 0
    fi
    
    # Check for source pattern like "[Source > Section"
    if grep -qE "\[.*>.*," "$response_file" 2>/dev/null; then
        return 0
    fi
    
    return 1
}

check_has_citations() {
    local response_file="$1"
    
    # Check for "Citations:" or "**Citations:**" pattern
    if grep -qi "citations:" "$response_file" 2>/dev/null; then
        return 0
    fi
    
    # Check for quote pattern (lines starting with >)
    if grep -qE "^> \"" "$response_file" 2>/dev/null; then
        return 0
    fi
    
    return 1
}

check_has_dont_know() {
    local response_file="$1"
    
    # Check for "don't know" or "don't have sufficient information" patterns
    if grep -qiE "(don't know|don't have sufficient|insufficient information|cannot answer|not enough information|unclear)" "$response_file" 2>/dev/null; then
        return 0
    fi
    
    # Russian equivalents
    if grep -qiE "(не знаю|недостаточно информации|не могу ответить|мало данных)" "$response_file" 2>/dev/null; then
        return 0
    fi
    
    return 1
}

check_meaning_matches() {
    local response_file="$1"
    local question="$2"
    
    # Check if response contains relevant content (not just generic response)
    # This is a heuristic: check if response length is reasonable (> 100 chars)
    local response_length=$(wc -c < "$response_file" | tr -d ' ')
    
    if [ "$response_length" -gt 100 ]; then
        return 0
    fi
    
    return 1
}

echo "================================================================"
echo "  ENHANCED RAG VALIDATION TEST"
echo "================================================================"
echo "  Testing: sources, citations, and 'don't know' mode"
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

# Step 1: Clean up and create embeddings
echo "================================================================"
echo "  STEP 1: Setting up embeddings"
echo "================================================================"
if [ -f "$EMBEDDINGS_DB" ]; then
    rm -f "$EMBEDDINGS_DB"
    rm -f "${EMBEDDINGS_DB}-shm"
    rm -f "${EMBEDDINGS_DB}-wal"
    echo "✓ Removed existing embeddings database"
else
    echo "✓ No existing embeddings database found"
fi

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
    count=$(sqlite3 "$EMBEDDINGS_DB" "SELECT COUNT(*) FROM embedding_index;" 2>/dev/null || echo "unknown")
    echo "  Total chunks in database: $count"
else
    echo "ERROR: Embeddings database not created"
    exit 1
fi
echo ""

# Step 2: Run questions and validate
echo "================================================================"
echo "  STEP 2: Running $TOTAL_QUESTIONS questions with validation"
echo "================================================================"
echo ""

> "$RESULTS_FILE"

for i in "${!QUESTIONS[@]}"; do
    question="${QUESTIONS[$i]}"
    echo "================================================================"
    echo "  Question $((i+1))/$TOTAL_QUESTIONS"
    echo "================================================================"
    echo "Q: $question"
    echo ""

    answer_file="$TMP_DIR/answer_$i.txt"
    stage_file="$TMP_DIR/stage_$i.txt"

    send_question "$question" "$answer_file" "$stage_file"

    # Load stage info
    if [ -f "$stage_file" ]; then
        source "$stage_file"
    else
        low_relevance="false"
        response_type="UNKNOWN"
    fi

    # Validate response
    echo "  Validation:"

    # Check for sources
    has_sources=false
    if check_has_sources "$answer_file"; then
        echo "    ✓ Has sources"
        has_sources=true
        QUESTIONS_WITH_SOURCES=$((QUESTIONS_WITH_SOURCES + 1))
    else
        echo "    ✗ Missing sources"
    fi

    # Check for citations
    has_citations=false
    if check_has_citations "$answer_file"; then
        echo "    ✓ Has citations"
        has_citations=true
        QUESTIONS_WITH_CITATIONS=$((QUESTIONS_WITH_CITATIONS + 1))
    else
        echo "    ✗ Missing citations"
    fi

    # Check for "don't know" mode
    has_dont_know=false
    if check_has_dont_know "$answer_file"; then
        echo "    ⚠ 'Don't know' mode triggered"
        has_dont_know=true
        QUESTIONS_WITH_DONT_KNOW=$((QUESTIONS_WITH_DONT_KNOW + 1))
    else
        echo "    ✓ Normal response"
    fi

    # Check if meaning matches (heuristic)
    if check_meaning_matches "$answer_file" "$question"; then
        echo "    ✓ Meaning appears to match context"
        MEANING_MATCHES=$((MEANING_MATCHES + 1))
    else
        echo "    ✗ Meaning may not match context"
    fi

    # Show low relevance info
    if [ "$low_relevance" = "true" ]; then
        echo "    ℹ Low relevance detected (search results not very relevant)"
    fi

    # Save detailed results
    echo "Q$((i+1)): $question" >> "$RESULTS_FILE"
    echo "  Has sources: $has_sources" >> "$RESULTS_FILE"
    echo "  Has citations: $has_citations" >> "$RESULTS_FILE"
    echo "  Don't know mode: $has_dont_know" >> "$RESULTS_FILE"
    echo "  Low relevance: $low_relevance" >> "$RESULTS_FILE"
    echo "  Response type: $response_type" >> "$RESULTS_FILE"
    echo "" >> "$RESULTS_FILE"
    echo "  Response (first 800 chars):" >> "$RESULTS_FILE"
    head -c 800 "$answer_file" >> "$RESULTS_FILE"
    echo "" >> "$RESULTS_FILE"
    echo "---" >> "$RESULTS_FILE"
    echo "" >> "$RESULTS_FILE"

    echo ""
done

# Step 3: Print summary
echo "================================================================"
echo "  VALIDATION SUMMARY"
echo "================================================================"
echo ""
echo "Questions tested:               $TOTAL_QUESTIONS"
echo "Questions with sources:         $QUESTIONS_WITH_SOURCES / $TOTAL_QUESTIONS"
echo "Questions with citations:       $QUESTIONS_WITH_CITATIONS / $TOTAL_QUESTIONS"
echo "Questions with 'don't know':    $QUESTIONS_WITH_DONT_KNOW / $TOTAL_QUESTIONS"
echo "Meaning matches context:        $MEANING_MATCHES / $TOTAL_QUESTIONS"
echo ""

# Calculate percentages
if [ "$TOTAL_QUESTIONS" -gt 0 ]; then
    sources_pct=$((QUESTIONS_WITH_SOURCES * 100 / TOTAL_QUESTIONS))
    citations_pct=$((QUESTIONS_WITH_CITATIONS * 100 / TOTAL_QUESTIONS))
    dont_know_pct=$((QUESTIONS_WITH_DONT_KNOW * 100 / TOTAL_QUESTIONS))
    matches_pct=$((MEANING_MATCHES * 100 / TOTAL_QUESTIONS))
    
    echo "Success rates:"
    echo "  Sources present:            ${sources_pct}%"
    echo "  Citations present:          ${citations_pct}%"
    echo "  'Don't know' triggered:     ${dont_know_pct}%"
    echo "  Meaning matches:            ${matches_pct}%"
fi

echo ""
echo "================================================================"
echo "  EXPECTED BEHAVIOR"
echo "================================================================"
echo ""
echo "Questions 1-5, 7-8, 10 (C programming topics):"
echo "  ✓ Should have sources and citations"
echo "  ✓ Should have normal responses (no 'don't know')"
echo ""
echo "Questions 6, 9 (Mars capital, quantum entanglement in C):"
echo "  ⚠ Should trigger 'don't know' mode (low relevance)"
echo "  ✓ Should ask for clarification"
echo ""

# Print detailed results
echo "================================================================"
echo "  DETAILED RESULTS"
echo "================================================================"
echo ""
cat "$RESULTS_FILE"

echo "================================================================"
echo "  TEST COMPLETE"
echo "================================================================"
echo ""

# Exit with error if success rate is too low
if [ "$QUESTIONS_WITH_SOURCES" -lt 7 ]; then
    echo "⚠ WARNING: Less than 70% of questions have sources!"
    exit 1
fi

if [ "$QUESTIONS_WITH_CITATIONS" -lt 7 ]; then
    echo "⚠ WARNING: Less than 70% of questions have citations!"
    exit 1
fi

echo "✓ All validation checks passed!"
exit 0
