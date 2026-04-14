#!/bin/bash

# =============================================================================
# AIChat Demo Script - GitHub MCP + Knowledge MCP Natural Language Usage
# =============================================================================
# This script demonstrates how to use AIChat with GitHub and Knowledge MCP
# servers via natural language prompts through the AIChat API
# =============================================================================

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

AICHAT_URL="http://localhost:8080"
GITHUB_MCP_URL="http://localhost:8083"
KNOWLEDGE_MCP_URL="http://localhost:8082"

print_header() {
    echo -e "\n${GREEN}================================================================${NC}"
    echo -e "${GREEN}  $1${NC}"
    echo -e "${GREEN}================================================================${NC}\n"
}

print_step() {
    echo -e "\n${YELLOW}>>> $1${NC}\n"
}

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_demo() {
    echo -e "\n${CYAN}📝 Natural Language Prompt:${NC}"
    echo -e "  \"$1\""
}

print_response() {
    echo -e "\n${GREEN}🤖 AIChat Response:${NC}"
    echo -e "$1" | sed 's/^/  /'
    echo ""
}

print_header "AIChat GitHub + Knowledge MCP Demo"

# Check servers
print_step "Checking server availability..."

servers_ok=true

echo -n "AIChat Server (8080)... "
if curl -s "$AICHAT_URL" >/dev/null 2>&1; then
    print_success "Running"
else
    echo -e "${RED}Not running${NC}"
    echo -e "${YELLOW}Start with: cd aichat && mvn spring-boot:run${NC}"
    servers_ok=false
fi

echo -n "GitHub MCP Server (8083)... "
if curl -s "$GITHUB_MCP_URL" >/dev/null 2>&1; then
    print_success "Running"
else
    echo -e "${RED}Not running${NC}"
    echo -e "${YELLOW}Start with: cd mcp-github && export GITHUB_TOKEN=xxx && mvn spring-boot:run${NC}"
    servers_ok=false
fi

echo -n "Knowledge MCP Server (8082)... "
if curl -s "$KNOWLEDGE_MCP_URL" >/dev/null 2>&1; then
    print_success "Running"
else
    echo -e "${RED}Not running${NC}"
    echo -e "${YELLOW}Start with: cd mcp-knowledge && mvn spring-boot:run${NC}"
    servers_ok=false
fi

if [ "$servers_ok" = false ]; then
    echo -e "\n${RED}Some servers are not running. Please start them before running the demo.${NC}"
    exit 1
fi

print_header "Demo 1: Using GitHub MCP via Natural Language"

print_step "Command 1: Connect to GitHub MCP Server"

print_demo "/mcp_github_connect"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "/mcp_github_connect"}')

if [ -n "$response" ]; then
    print_response "$response"
else
    print_info "No response received (may need manual connection)"
fi

print_step "Command 2: Check GitHub Connection Status"

print_demo "/mcp_github_status"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "/mcp_github_status"}')

if [ -n "$response" ]; then
    print_response "$response"
else
    print_info "No response received"
fi

print_step "Command 3: List Available GitHub Tools"

print_demo "/mcp_github_list"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "/mcp_github_list"}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -30)..."
else
    print_info "No response received"
fi

print_header "Demo 2: Clone Repository via Natural Language"

print_step "Natural Language: Clone tinyAI Repository"

print_demo "Please clone the repository https://github.com/Headmast/tinyAI from the 20task branch"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "Please clone the repository https://github.com/Headmast/tinyAI from the 20task branch"}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -20)..."
else
    print_info "No response received"
fi

print_header "Demo 3: Explore Repository Structure"

print_step "Natural Language: Show Repository Structure"

print_demo "Show me the structure of the tinyAI repository in github-repos/tinyAI"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "Show me the structure of the tinyAI repository in github-repos/tinyAI, max depth 2 levels"}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -30)..."
else
    print_info "No response received"
fi

print_header "Demo 4: Search for Similar Repositories"

print_step "Natural Language: Search GitHub"

print_demo "Search GitHub for AI agent implementations using Java and Spring Boot, sorted by stars"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "Search GitHub for AI agent implementations using Java and Spring Boot, sorted by stars"}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -30)..."
else
    print_info "No response received"
fi

print_header "Demo 5: Read File Contents"

print_step "Natural Language: Read README"

print_demo "Read the README.md file from the tinyAI repository and tell me what it's about"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "Read the README.md file from github-repos/tinyAI and tell me what the project is about"}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -30)..."
else
    print_info "No response received"
fi

print_header "Demo 6: List Issues and Pull Requests"

print_step "Natural Language: List GitHub Issues"

print_demo "Show me the open issues in the Headmast/tinyAI repository"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "Show me the open issues in the Headmast/tinyAI repository"}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -20)..."
else
    print_info "No response received"
fi

print_step "Natural Language: List Pull Requests"

print_demo "List all pull requests in the Headmast/tinyAI repository"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "List all pull requests in the Headmast/tinyAI repository"}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -20)..."
else
    print_info "No response received"
fi

print_header "Demo 7: Save Knowledge via Natural Language"

print_step "Natural Language: Save Repository Analysis"

print_demo "Save this knowledge: The tinyAI repository is a minimal AI agent implementation using Spring Boot with 23 Java files and Maven build system. It demonstrates how to create AI agents with LangChain integration."

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "Save this knowledge: The tinyAI repository is a minimal AI agent implementation using Spring Boot with 23 Java files and Maven build system. It demonstrates how to create AI agents with LangChain integration."}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -15)..."
else
    print_info "No response received"
fi

print_header "Demo 8: Query Saved Knowledge"

print_step "Natural Language: Query Knowledge"

print_demo "What do you know about the tinyAI repository?"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "What do you know about the tinyAI repository?"}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -30)..."
else
    print_info "No response received"
fi

print_step "Natural Language: Compare Repositories"

print_demo "Compare the three repositories we analyzed for Day 20 task"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "Compare the three repositories we analyzed for Day 20 task: tinyAI, agent_challenge, and ai-advent-challenge-tasks. What are their main differences in terms of technology stack and purpose?"}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -40)..."
else
    print_info "No response received"
fi

print_header "Demo 9: Advanced GitHub Operations"

print_step "Natural Language: Create Branch"

print_demo "Create a new branch called 'day20-analysis' in the tinyAI repository"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "Create a new branch called day20-analysis in the github-repos/tinyAI repository"}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -10)..."
else
    print_info "No response received"
fi

print_step "Natural Language: Get README"

print_demo "Show me the README for DieOfCode/agent_challenge repository on GitHub"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "Show me the README for DieOfCode/agent_challenge repository on GitHub"}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -30)..."
else
    print_info "No response received"
fi

print_header "Demo 10: Complex Multi-Step Task"

print_step "Natural Language: Complex Workflow"

print_demo "Clone the agent_challenge repository, analyze its structure, read the main Python files, and save a summary of what technologies and libraries it uses"

response=$(curl -s -X POST "$AICHAT_URL/chat" \
    -H "Content-Type: application/json" \
    -d '{"prompt": "Clone the repository https://github.com/DieOfCode/agent_challenge from branch codex/day20, analyze its structure, and save a summary of what technologies and libraries it uses"}')

if [ -n "$response" ]; then
    print_response "$(echo "$response" | head -40)..."
else
    print_info "No response received"
fi

print_header "Demo Complete!"

echo -e "${GREEN}Summary:${NC}"
echo "  ✓ Connected to GitHub MCP server"
echo "  ✓ Cloned repositories via natural language"
echo "  ✓ Explored repository structures"
echo "  ✓ Searched GitHub for similar projects"
echo "  ✓ Read file contents"
echo "  ✓ Listed issues and pull requests"
echo "  ✓ Saved knowledge to Knowledge MCP"
echo "  ✓ Queried saved knowledge"
echo "  ✓ Performed advanced Git operations"
echo "  ✓ Executed complex multi-step workflows"
echo ""
echo -e "${CYAN}What You Learned:${NC}"
echo "  • How to use GitHub MCP via natural language in AIChat"
echo "  • How to clone, explore, and analyze repositories"
echo "  • How to save insights to Knowledge MCP for future reference"
echo "  • How to query saved knowledge using natural language"
echo "  • How to perform complex multi-step workflows automatically"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "  1. Open AIChat in browser: http://localhost:8080"
echo "  2. Try your own natural language prompts"
echo "  3. Explore more repositories on GitHub"
echo "  4. Save your findings to Knowledge MCP"
echo "  5. Use /mcp_github_list to see all available tools"
echo ""
echo -e "${BLUE}Example prompts to try:${NC}"
echo '  • "Create a pull request in tinyAI from day20-analysis to 20task"'
echo '  • "What are the dependencies in agent_challenge?"'
echo '  • "Search for Python machine learning repos"'
echo '  • "Show me Day 20 task information"'
echo '  • "Commit my changes and push to GitHub"'
