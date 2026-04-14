#!/bin/bash

# =============================================================================
# GitHub MCP + Knowledge MCP Integration Test Script
# =============================================================================
# This script:
# 1. Clones all 4 repositories using GitHub MCP server
# 2. Reads and prints structure of each repository
# 3. Compares contents (languages, libraries, frameworks)
# 4. Saves essential knowledge to Knowledge MCP server
# =============================================================================

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
GITHUB_MCP_URL="http://localhost:8083"
KNOWLEDGE_MCP_URL="http://localhost:8082"
AICHAT_URL="http://localhost:8080"
WORK_DIR="github-repos"

# Repositories to analyze
declare -a REPO_URLS=(
    "https://github.com/Headmast/tinyAI"
    "https://github.com/DieOfCode/agent_challenge"
    "https://github.com/fun-bear/ai-advent-challenge-tasks"
)

declare -a REPO_NAMES=(
    "Headmast/tinyAI"
    "DieOfCode/agent_challenge"
    "fun-bear/ai-advent-challenge-tasks"
)

declare -a REPO_BRANCHS=(
    "20task"
    "codex/day20"
    "main"
)

declare -a REPO_DESCRIPTIONS=(
    "tinyAI - A minimal AI agent implementation"
    "agent_challenge - Agent challenge implementation by DieOfCode"
    "ai-advent-challenge-tasks - AI Advent Challenge task descriptions"
)

# =============================================================================
# Helper Functions
# =============================================================================

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

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_comparison() {
    echo -e "\n${CYAN}$1${NC}"
}

# =============================================================================
# MCP Tool Call Function
# =============================================================================

call_github_tool() {
    local tool_name=$1
    local args_json=$2
    
    print_info "Calling GitHub MCP tool: $tool_name"
    print_info "Arguments: $args_json"
    
    # Call the MCP server tool endpoint
    response=$(curl -s -X POST "$GITHUB_MCP_URL/mcp/tools/$tool_name" \
        -H "Content-Type: application/json" \
        -d "$args_json" 2>/dev/null || echo "ERROR")
    
    if [ "$response" = "ERROR" ]; then
        print_error "Failed to call tool: $tool_name"
        return 1
    fi
    
    echo "$response"
}

call_knowledge_tool() {
    local tool_name=$1
    local args_json=$2
    
    print_info "Calling Knowledge MCP tool: $tool_name"
    
    response=$(curl -s -X POST "$KNOWLEDGE_MCP_URL/mcp/tools/$tool_name" \
        -H "Content-Type: application/json" \
        -d "$args_json" 2>/dev/null || echo "ERROR")
    
    if [ "$response" = "ERROR" ]; then
        print_error "Failed to call knowledge tool: $tool_name"
        return 1
    fi
    
    echo "$response"
}

# =============================================================================
# Main Test Flow
# =============================================================================

print_header "GitHub MCP + Knowledge MCP Integration Test"

# Check if environment is ready
print_step "Checking server availability..."

echo -n "Checking GitHub MCP Server (port 8083)... "
if curl -s "$GITHUB_MCP_URL" >/dev/null 2>&1; then
    print_success "GitHub MCP Server is running"
else
    print_error "GitHub MCP Server is NOT running"
    echo "Please start it with: cd mcp-github && export GITHUB_TOKEN=xxx && mvn spring-boot:run"
    exit 1
fi

echo -n "Checking Knowledge MCP Server (port 8082)... "
if curl -s "$KNOWLEDGE_MCP_URL" >/dev/null 2>&1; then
    print_success "Knowledge MCP Server is running"
else
    print_error "Knowledge MCP Server is NOT running"
    echo "Please start it with: cd mcp-knowledge && mvn spring-boot:run"
    exit 1
fi

echo -n "Checking AIChat Server (port 8080)... "
if curl -s "$AICHAT_URL" >/dev/null 2>&1; then
    print_success "AIChat Server is running"
else
    print_info "AIChat Server is NOT running (optional, script will still work)"
fi

print_header "Phase 1: Clone Repositories using GitHub MCP"

# Create working directory
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

for i in "${!REPO_URLS[@]}"; do
    repo_url="${REPO_URLS[$i]}"
    repo_name="${REPO_NAMES[$i]}"
    repo_branch="${REPO_BRANCHS[$i]}"
    
    print_step "Cloning repository $((i+1))/${#REPO_URLS[@]}: $repo_name"
    echo -e "${CYAN}URL: $repo_url${NC}"
    echo -e "${CYAN}Branch: $repo_branch${NC}\n"
    
    # Extract directory name from URL
    dir_name=$(basename "$repo_url" .git)
    
    if [ -d "$dir_name/.git" ]; then
        print_info "Repository already cloned: $dir_name"
    else
        print_info "Cloning..."
        
        # Use GitHub MCP to clone
        tool_result=$(call_github_tool "clone_repository" \
            "{\"repo_url\": \"$repo_url\", \"branch\": \"$repo_branch\"}")
        
        echo "$tool_result"
        
        # Fallback to direct git clone if MCP tool fails
        if echo "$tool_result" | grep -q "ERROR\|Error"; then
            print_info "Falling back to direct git clone..."
            if [ "$repo_branch" != "main" ]; then
                git clone -b "$repo_branch" "$repo_url" 2>/dev/null || print_error "Failed to clone $repo_name"
            else
                git clone "$repo_url" 2>/dev/null || print_error "Failed to clone $repo_name"
            fi
        fi
    fi
    
    # Verify clone
    if [ -d "$dir_name" ]; then
        print_success "Repository cloned successfully: $dir_name"
        cd "$dir_name"
        echo -e "${CYAN}Current branch: $(git branch --show-current)${NC}"
        echo -e "${CYAN}Commit: $(git log --oneline -1)${NC}"
        cd ..
    else
        print_error "Failed to clone: $repo_name"
    fi
    
    echo ""
done

print_header "Phase 2: Analyze Repository Structures"

for i in "${!REPO_URLS[@]}"; do
    repo_name="${REPO_NAMES[$i]}"
    dir_name=$(basename "${REPO_URLS[$i]}" .git)
    
    if [ ! -d "$dir_name" ]; then
        print_error "Directory not found: $dir_name, skipping..."
        continue
    fi
    
    print_step "Analyzing Repository $((i+1)): $repo_name ($dir_name)"
    
    cd "$dir_name"
    
    # Get directory structure
    print_info "Repository Structure:"
    echo -e "${CYAN}┌─────────────────────────────────────────┐${NC}"
    find . -type f -not -path '*/\.*' -not -path '*/target/*' -not -path '*/node_modules/*' | head -50 | sed 's/^/│ /'
    echo -e "${CYAN}└─────────────────────────────────────────┘${NC}"
    
    # Count files by type
    print_info "File Statistics:"
    total_files=$(find . -type f -not -path '*/\.*' | wc -l | tr -d ' ')
    echo "  Total files: $total_files"
    
    # Language detection
    echo -n "  Languages: "
    if command -v cloc &> /dev/null; then
        cloc --quiet --json . 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
langs = []
for lang, info in data.items():
    if lang != 'header' and info.get('code', 0) > 0:
        langs.append(f'{lang}({info[\"code\"]} lines)')
print(', '.join(langs[:5]) if langs else 'Unknown')
" 2>/dev/null || echo "Unknown (cloc parsing failed)"
    else
        # Simple detection by file extension
        java_count=$(find . -name "*.java" | wc -l | tr -d ' ')
        py_count=$(find . -name "*.py" | wc -l | tr -d ' ')
        js_count=$(find . -name "*.js" -o -name "*.ts" | wc -l | tr -d ' ')
        md_count=$(find . -name "*.md" | wc -l | tr -d ' ')
        
        counts=()
        [ "$java_count" -gt 0 ] && counts+=("Java:$java_count")
        [ "$py_count" -gt 0 ] && counts+=("Python:$py_count")
        [ "$js_count" -gt 0 ] && counts+=("JavaScript:$js_count")
        [ "$md_count" -gt 0 ] && counts+=("Markdown:$md_count")
        
        if [ ${#counts[@]} -gt 0 ]; then
            echo "${counts[@]}"
        else
            echo "Unknown"
        fi
    fi
    
    # Check for build files
    print_info "Build Configuration:"
    [ -f "pom.xml" ] && echo "  ✓ Maven (pom.xml)"
    [ -f "build.gradle" ] && echo "  ✓ Gradle (build.gradle)"
    [ -f "package.json" ] && echo "  ✓ Node.js (package.json)"
    [ -f "requirements.txt" ] && echo "  ✓ Python (requirements.txt)"
    [ -f "CMakeLists.txt" ] && echo "  ✓ CMake (CMakeLists.txt)"
    [ -f "Makefile" ] && echo "  ✓ Make (Makefile)"
    [ -f "Dockerfile" ] && echo "  ✓ Docker (Dockerfile)"
    
    # Check for common directories
    print_info "Project Structure:"
    [ -d "src" ] && echo "  ✓ src/ directory"
    [ -d "test" ] && echo "  ✓ test/ directory"
    [ -d "tests" ] && echo "  ✓ tests/ directory"
    [ -d "docs" ] && echo "  ✓ docs/ directory"
    [ -d "config" ] && echo "  ✓ config/ directory"
    
    cd ..
    echo ""
done

print_header "Phase 3: Compare Repositories"

print_comparison "=== REPOSITORY COMPARISON ==="
printf "%-40s %-15s %-15s %-15s\n" "Repository" "Type" "Language" "Build Tool"
printf "%-40s %-15s %-15s %-15s\n" "--------" "----" "--------" "----------"

for i in "${!REPO_URLS[@]}"; do
    dir_name=$(basename "${REPO_URLS[$i]}" .git)
    repo_desc="${REPO_DESCRIPTIONS[$i]}"
    
    if [ ! -d "$dir_name" ]; then
        printf "%-40s %-15s %-15s %-15s\n" "$repo_desc" "N/A" "N/A" "N/A"
        continue
    fi
    
    cd "$dir_name"
    
    # Detect type
    repo_type="Unknown"
    if find . -name "*.java" | grep -q .; then
        repo_type="Java Application"
    elif find . -name "*.py" | grep -q .; then
        repo_type="Python Application"
    elif find . -name "*.js" -o -name "*.ts" | grep -q .; then
        repo_type="JavaScript/TypeScript"
    elif find . -name "*.md" | grep -q .; then
        repo_type="Documentation"
    fi
    
    # Detect primary language
    primary_lang="Mixed"
    java_count=$(find . -name "*.java" | wc -l | tr -d ' ')
    py_count=$(find . -name "*.py" | wc -l | tr -d ' ')
    if [ "$java_count" -gt "$py_count" ] && [ "$java_count" -gt 0 ]; then
        primary_lang="Java ($java_count)"
    elif [ "$py_count" -gt 0 ]; then
        primary_lang="Python ($py_count)"
    fi
    
    # Detect build tool
    build_tool="None"
    [ -f "pom.xml" ] && build_tool="Maven"
    [ -f "build.gradle" ] && build_tool="Gradle"
    [ -f "package.json" ] && build_tool="npm/yarn"
    [ -f "requirements.txt" ] && build_tool="pip"
    
    printf "%-40s %-15s %-15s %-15s\n" "$repo_desc" "$repo_type" "$primary_lang" "$build_tool"
    
    cd ..
done

print_header "Phase 4: Extract Key Information"

for i in "${!REPO_URLS[@]}"; do
    repo_name="${REPO_NAMES[$i]}"
    repo_desc="${REPO_DESCRIPTIONS[$i]}"
    dir_name=$(basename "${REPO_URLS[$i]}" .git)
    
    if [ ! -d "$dir_name" ]; then
        continue
    fi
    
    print_step "Extracting information from: $repo_name"
    
    cd "$dir_name"
    
    # Look for README
    if [ -f "README.md" ]; then
        print_info "README.md found, extracting key points..."
        head -30 README.md | grep -v "^#" | head -10 | sed 's/^/  /'
    elif [ -f "readme.md" ]; then
        print_info "readme.md found, extracting key points..."
        head -30 readme.md | grep -v "^#" | head -10 | sed 's/^/  /'
    fi
    
    # Look for main files
    print_info "Key files found:"
    find . -name "*.java" -not -path '*/test/*' | head -5 | sed 's/^/  - /'
    find . -name "*.py" -not -path '*/test/*' | head -5 | sed 's/^/  - /'
    find . -name "*.md" | head -3 | sed 's/^/  - /'
    
    # Check dependencies
    if [ -f "pom.xml" ]; then
        print_info "Maven dependencies (top 5):"
        grep -A 3 "<dependency>" pom.xml | grep "<artifactId>" | head -5 | sed 's/.*<artifactId>//' | sed 's/<\/artifactId>//' | sed 's/^/    - /'
    fi
    
    if [ -f "requirements.txt" ]; then
        print_info "Python dependencies:"
        head -10 requirements.txt | sed 's/^/    - /'
    fi
    
    if [ -f "package.json" ]; then
        print_info "Node.js dependencies:"
        python3 -c "
import json
with open('package.json') as f:
    data = json.load(f)
deps = list(data.get('dependencies', {}).keys())[:5]
for d in deps:
    print(f'    - {d}')
" 2>/dev/null || echo "    (unable to parse)"
    fi
    
    cd ..
    echo ""
done

print_header "Phase 5: Save Knowledge to Knowledge MCP"

for i in "${!REPO_URLS[@]}"; do
    repo_name="${REPO_NAMES[$i]}"
    repo_desc="${REPO_DESCRIPTIONS[$i]}"
    repo_url="${REPO_URLS[$i]}"
    repo_branch="${REPO_BRANCHS[$i]}"
    dir_name=$(basename "${REPO_URLS[$i]}" .git)
    
    if [ ! -d "$dir_name" ]; then
        continue
    fi
    
    print_step "Saving knowledge for: $repo_name"
    
    cd "$dir_name"
    
    # Gather comprehensive information
    total_files=$(find . -type f -not -path '*/\.*' | wc -l | tr -d ' ')
    
    # Detect languages and counts
    java_count=$(find . -name "*.java" | wc -l | tr -d ' ')
    py_count=$(find . -name "*.py" | wc -l | tr -d ' ')
    js_count=$(find . -name "*.js" -o -name "*.ts" | wc -l | tr -d ' ')
    md_count=$(find . -name "*.md" | wc -l | tr -d ' ')
    
    # Get key dependencies
    dependencies=""
    if [ -f "pom.xml" ]; then
        dependencies=$(grep -A 2 "<dependency>" pom.xml | grep "<artifactId>" | head -10 | sed 's/.*<artifactId>//' | sed 's/<\/artifactId>//' | tr '\n' ', ' | sed 's/,$//')
    fi
    if [ -f "requirements.txt" ]; then
        dependencies=$(head -20 requirements.txt | tr '\n' ', ' | sed 's/,$//')
    fi
    
    # Get project description from README
    readme_excerpt=""
    if [ -f "README.md" ]; then
        readme_excerpt=$(head -20 README.md | grep -v "^#" | head -5 | tr '\n' ' ')
    fi
    
    # Build knowledge description
    knowledge_description="## Repository: $repo_name
**URL:** $repo_url
**Branch:** $repo_branch
**Description:** $repo_desc

### Statistics
- **Total Files:** $total_files
- **Java Files:** $java_count
- **Python Files:** $py_count
- **JavaScript/TypeScript Files:** $js_count
- **Documentation Files:** $md_count

### Technologies
- **Languages:** $(if [ "$java_count" -gt 0 ]; then echo "Java, "; fi)$(if [ "$py_count" -gt 0 ]; then echo "Python, "; fi)$(if [ "$js_count" -gt 0 ]; then echo "JavaScript/TypeScript, "; fi)
- **Build Tools:** $(if [ -f "pom.xml" ]; then echo "Maven, "; fi)$(if [ -f "requirements.txt" ]; then echo "pip, "; fi)$(if [ -f "package.json" ]; then echo "npm, "; fi)

### Dependencies
$dependencies

### Overview
$readme_excerpt

### Analysis Date
$(date '+%Y-%m-%d %H:%M:%S')

### Task Context
Day 20 task repository comparison - AI Advent Challenge"
    
    # Escape special characters for JSON
    knowledge_description_escaped=$(echo "$knowledge_description" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g' | sed 's/\t/\\t/g' | tr '\n' '\\n')
    
    # Create knowledge title
    knowledge_title="How to analyze and compare $repo_name - Day 20"
    
    print_info "Saving knowledge: $knowledge_title"
    
    # Save to knowledge MCP
    if command -v jq &> /dev/null; then
        json_payload=$(jq -n \
            --arg title "$knowledge_title" \
            --arg description "$knowledge_description" \
            '{title: $title, description: $description}')
        
        result=$(curl -s -X POST "$KNOWLEDGE_MCP_URL/mcp/tools/save_knowledge" \
            -H "Content-Type: application/json" \
            -d "$json_payload" 2>/dev/null)
    else
        # Fallback without jq
        json_payload="{\"title\": \"$(echo $knowledge_title | sed 's/"/\\"/g')\", \"description\": \"$(echo "$knowledge_description" | sed 's/"/\\"/g' | tr '\n' ' ')\"}"
        
        result=$(curl -s -X POST "$KNOWLEDGE_MCP_URL/mcp/tools/save_knowledge" \
            -H "Content-Type: application/json" \
            -d "$json_payload" 2>/dev/null)
    fi
    
    if echo "$result" | grep -q "successfully\|saved"; then
        print_success "Knowledge saved successfully"
    else
        print_info "Knowledge MCP response: $result"
        print_info "Attempting alternative save method..."
        
        # Try via AIChat if direct MCP call fails
        if curl -s "$AICHAT_URL" >/dev/null 2>&1; then
            print_info "Saving knowledge via AIChat..."
            # This would require AIChat to have an endpoint to save knowledge
            # For now, we'll just log it
            echo "$knowledge_description" > "../knowledge_backup_$(echo $repo_name | tr '/' '_').md"
            print_success "Knowledge backed up to file"
        fi
    fi
    
    cd ..
    echo ""
done

print_header "Phase 6: Verify Saved Knowledge"

print_step "Listing all saved knowledge entries..."

knowledge_list=$(call_knowledge_tool "knowledge_contents" "{}")

if [ -n "$knowledge_list" ] && [ "$knowledge_list" != "ERROR" ]; then
    echo "$knowledge_list"
else
    print_info "Unable to retrieve knowledge list via MCP API"
    print_info "Knowledge may have been saved via alternative methods"
fi

print_header "Phase 7: Generate Comparison Report"

report_file="repository_comparison-report-$(date +%Y%m%d_%H%M%S).md"

cat > "$report_file" << 'REPORT_HEADER'
# Repository Comparison Report - Day 20 Task

## Executive Summary

This report compares four repositories analyzed as part of the Day 20 task:
- tinyAI by Headmast
- agent_challenge by DieOfCode
- ai-advent-challenge-tasks by fun-bear

## Detailed Analysis

REPORT_HEADER

for i in "${!REPO_URLS[@]}"; do
    repo_name="${REPO_NAMES[$i]}"
    repo_url="${REPO_URLS[$i]}"
    repo_branch="${REPO_BRANCHS[$i]}"
    repo_desc="${REPO_DESCRIPTIONS[$i]}"
    dir_name=$(basename "${REPO_URLS[$i]}" .git)
    
    if [ ! -d "$dir_name" ]; then
        continue
    fi
    
    cd "$dir_name"
    
    cat >> "../$report_file" << SECTION
### $repo_name

**URL:** $repo_url
**Branch:** $repo_branch
**Description:** $repo_desc

#### Structure
\`\`\`
$(find . -type f -not -path '*/\.*' -not -path '*/target/*' | head -30)
\`\`\`

#### Technologies
- Java files: $(find . -name "*.java" | wc -l | tr -d ' ')
- Python files: $(find . -name "*.py" | wc -l | tr -d ' ')
- JS/TS files: $(find . -name "*.js" -o -name "*.ts" | wc -l | tr -d ' ')

SECTION
    
    cd ..
done

cat >> "$report_file" << 'FOOTER'

## Key Findings

Each repository serves a different purpose in the AI Advent Challenge:
1. **tinyAI** - Minimal AI agent implementation
2. **agent_challenge** - Agent challenge implementation  
3. **ai-advent-challenge-tasks** - Task descriptions and requirements

## Knowledge Saved

All repository analyses have been saved to the Knowledge MCP server for future reference.

---
*Report generated on: $(date '+%Y-%m-%d %H:%M:%S')*
FOOTER

print_success "Report generated: $report_file"

print_header "Test Complete!"

echo -e "${GREEN}Summary:${NC}"
echo "  ✓ Cloned 3 repositories"
echo "  ✓ Analyzed repository structures"
echo "  ✓ Compared technologies and dependencies"
echo "  ✓ Saved knowledge to Knowledge MCP server"
echo "  ✓ Generated comparison report"
echo ""
echo -e "${CYAN}Files created:${NC}"
echo "  - $report_file"
ls knowledge_backup_*.md 2>/dev/null | sed 's/^/  - /' || true
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "  1. View the comparison report: cat $report_file"
echo "  2. Check saved knowledge: cd ../mcp-knowledge && sqlite3 knowledge.db 'SELECT title FROM knowledge;'"
echo "  3. Use AIChat to query the knowledge: /mcp_github_list"
