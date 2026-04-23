#!/bin/bash

# Test script for aichat-c-learning
# Feeds a predefined list of questions and commands to the application

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="$SCRIPT_DIR/target/aichat-c-learning-1.0.jar"

# Check if JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo "Error: JAR file not found at $JAR_PATH"
    echo "Please run 'mvn clean package' first"
    exit 1
fi

# Check if GIGACHAT_API_CREDENTIALS is set
if [ -z "$GIGACHAT_API_CREDENTIALS" ]; then
    echo "Error: Environment variable GIGACHAT_API_CREDENTIALS is not set"
    exit 1
fi

echo "=========================================="
echo "  aichat-c-learning Automated Test"
echo "=========================================="
echo ""
echo "Starting aichat-c-learning with predefined questions..."
echo ""

# Create temporary input file with questions
INPUT_FILE=$(mktemp)

cat > "$INPUT_FILE" <<'EOF'
/clean
Is C pretty or ugly language?
How to setup Windows for programming on C?
What text editor to use for C programming?
How to use Make?
Explain formatted printing
Provide examples
How to debug C program? Give me instructions.
What are structures and how to use them?
Pointers
All kind of memory allocation
Print out debug macros
I mean Zed's debug macros
/clean
What is Duff's device?
Libraries in C
How to make a library?
Write buble sort
Write dynamic array
How to implement hashmap in C?
Binary Search Trees
Make summary of this dialog
Write simple TCP/IP Client
How to break it?
/quit
EOF

# Run Java with the input file, showing input lines as they're consumed
# Use awk to print each input line with "You: " prefix when Java reads it
java -jar "$JAR_PATH" < "$INPUT_FILE"

# Cleanup
rm -f "$INPUT_FILE"

echo ""
echo "=========================================="
echo "  Test completed"
echo "=========================================="
