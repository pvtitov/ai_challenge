#!/bin/bash

# Test script to verify RAG, MCP, and Review functionality

echo "======================================"
echo "  Testing Aichat-GitHub Enhancements"
echo "======================================"
echo ""

# Test 1: Check RAG database
echo "Test 1: Checking RAG database..."
EMBEDDINGS_DB="private/tinyAI/embeddings.db"
if [ -f "$EMBEDDINGS_DB" ]; then
    COUNT=$(sqlite3 "$EMBEDDINGS_DB" "SELECT COUNT(*) FROM embedding_index;" 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "✓ RAG database found with $COUNT embeddings"
    else
        echo "✗ RAG database exists but cannot be queried"
    fi
else
    echo "✗ RAG database NOT found at: $EMBEDDINGS_DB"
fi
echo ""

# Test 2: Check if Ollama is running
echo "Test 2: Checking Ollama..."
if curl -s http://localhost:11434 >/dev/null 2>&1; then
    echo "✓ Ollama is running"
    
    # Check embedding model
    if ollama list 2>/dev/null | grep -q "nomic-embed-text"; then
        echo "✓ Embedding model (nomic-embed-text) is available"
    else
        echo "✗ Embedding model NOT found. Run: ollama pull nomic-embed-text"
    fi
    
    # Check chat model
    if ollama list 2>/dev/null | grep -q "llama3.2"; then
        echo "✓ Chat model (llama3.2) is available"
    else
        echo "⚠ Chat model may not be optimal. Recommended: ollama pull llama3.2:1b"
    fi
else
    echo "✗ Ollama is NOT running. Run: ollama serve"
fi
echo ""

# Test 3: Check if MCP server is running
echo "Test 3: Checking MCP server..."
if curl -s http://localhost:8083 >/dev/null 2>&1; then
    echo "✓ MCP server is running on port 8083"
else
    echo "⚠ MCP server is NOT running (will be started by run.sh)"
fi
echo ""

# Test 4: Check if tinyAI repo exists
echo "Test 4: Checking tinyAI repository..."
REPO_PATH="private/tinyAI"
if [ -d "$REPO_PATH/.git" ]; then
    echo "✓ Repository exists"
    cd "$REPO_PATH"
    
    # Check current branch
    CURRENT_BRANCH=$(git symbolic-ref --short HEAD 2>/dev/null)
    echo "  Current branch: $CURRENT_BRANCH"
    
    # Check commits
    COMMIT_COUNT=$(git log --oneline | wc -l | tr -d ' ')
    echo "  Total commits: $COMMIT_COUNT"
    
    # List branches
    BRANCH_COUNT=$(git branch | wc -l | tr -d ' ')
    echo "  Local branches: $BRANCH_COUNT"
    
    cd - > /dev/null
else
    echo "✗ Repository NOT found at: $REPO_PATH"
fi
echo ""

# Test 5: Build the project
echo "Test 5: Building project..."
mvn clean package -DskipTests -q
if [ $? -eq 0 ]; then
    echo "✓ Build successful"
else
    echo "✗ Build FAILED"
fi
echo ""

echo "======================================"
echo "  Test Summary"
echo "======================================"
echo ""
echo "To test interactively:"
echo "  ./run.sh"
echo ""
echo "Then try these commands:"
echo "  1. What is this project about?    (Tests RAG)"
echo "  2. show last commit                (Tests MCP)"
echo "  3. show branches                   (Tests MCP)"
echo "  4. /review                         (Tests Review)"
echo "  5. /review entire project          (Tests Full Review)"
echo ""
