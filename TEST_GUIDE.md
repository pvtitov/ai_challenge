# GitHub MCP + Knowledge MCP Integration Test Guide

This guide explains how to use the comprehensive test script that demonstrates the power of combining GitHub MCP and Knowledge MCP servers.

## What the Test Script Does

The `test_github_knowledge_mcp.sh` script performs a complete end-to-end test:

1. **Clones repositories** using GitHub MCP server
2. **Analyzes structure** of each repository
3. **Compares contents** (languages, frameworks, dependencies)
4. **Saves knowledge** to Knowledge MCP server
5. **Generates report** with detailed comparison

## Prerequisites

### 1. Set GitHub Token

```bash
export GITHUB_TOKEN=your_personal_access_token_here
```

Generate a token at: https://github.com/settings/tokens?type=beta

### 2. Start Required Servers

You need at least these two servers running:

**Terminal 1 - GitHub MCP Server:**
```bash
cd /Users/paveltitov/Documents/programming/ai_challenge/mcp-github
export GITHUB_TOKEN=your_token_here
mvn spring-boot:run
```

**Terminal 2 - Knowledge MCP Server:**
```bash
cd /Users/paveltitov/Documents/programming/ai_challenge/mcp-knowledge
mvn spring-boot:run
```

**Optional - AIChat Server (for web interface):**
```bash
cd /Users/paveltitov/Documents/programming/ai_challenge/aichat
mvn spring-boot:run
```

**Or use the all-in-one launcher:**
```bash
export GITHUB_TOKEN=your_token_here
./run_servers.sh
```

## Running the Test

### Quick Start

```bash
cd /Users/paveltitov/Documents/programming/ai_challenge
./test_github_knowledge_mcp.sh
```

### What Repositories Are Tested

The script analyzes these 3 repositories:

1. **Headmast/tinyAI** (branch: 20task)
   - URL: https://github.com/Headmast/tinyAI/tree/20task
   - A minimal AI agent implementation

2. **DieOfCode/agent_challenge** (branch: codex/day20)
   - URL: https://github.com/DieOfCode/agent_challenge/tree/codex/day20
   - Agent challenge implementation

3. **fun-bear/ai-advent-challenge-tasks** (branch: main)
   - URL: https://github.com/fun-bear/ai-advent-challenge-tasks
   - AI Advent Challenge task descriptions

Note: The gitverse.ru repository is excluded as it's on a different Git hosting service.

## Test Output

The script will display:

### Phase 1: Clone Repositories
```
>>> Cloning repository 1/3: Headmast/tinyAI
URL: https://github.com/Headmast/tinyAI
Branch: 20task

[INFO] Calling GitHub MCP tool: clone_repository
[SUCCESS] Repository cloned successfully: tinyAI
```

### Phase 2: Analyze Structures
```
>>> Analyzing Repository 1: Headmast/tinyAI (tinyAI)

[INFO] Repository Structure:
┌─────────────────────────────────────────┐
│ ./pom.xml
│ ./README.md
│ ./src/main/java/App.java
│ ...
└─────────────────────────────────────────┘

[INFO] File Statistics:
  Total files: 45
  Languages: Java(1234 lines), Python(567 lines)
```

### Phase 3: Compare Repositories
```
=== REPOSITORY COMPARISON ===
Repository                               Type            Language        Build Tool
--------                               ----            --------        ----------
tinyAI - A minimal AI agent            Java Application Java (23)       Maven
agent_challenge implementation         Python Application Python (45)   pip
ai-advent-challenge-tasks              Documentation    Mixed          None
```

### Phase 4: Extract Key Information
```
>>> Extracting information from: Headmast/tinyAI

[INFO] README.md found, extracting key points...
  A minimal AI agent implementation using Spring Boot

[INFO] Key files found:
  - src/main/java/com/example/App.java
  - src/main/java/com/example/Agent.java
  - README.md

[INFO] Maven dependencies (top 5):
    - spring-boot-starter-web
    - spring-ai-core
    - jackson-databind
```

### Phase 5: Save Knowledge
```
>>> Saving knowledge for: Headmast/tinyAI

[INFO] Saving knowledge: How to analyze and compare Headmast/tinyAI - Day 20
[SUCCESS] Knowledge saved successfully
```

### Phase 6: Verify Knowledge
```
>>> Listing all saved knowledge entries...

Knowledge titles matching regex "Day 20":
- How to analyze and compare Headmast/tinyAI - Day 20
- How to analyze and compare DieOfCode/agent_challenge - Day 20
- How to analyze and compare fun-bear/ai-advent-challenge-tasks - Day 20
```

### Phase 7: Generate Report
```
[SUCCESS] Report generated: repository_comparison-report-20260413_143022.md
```

## Knowledge MCP Integration

### What Gets Saved

For each repository, the script saves:

```markdown
## Repository: Headmast/tinyAI
**URL:** https://github.com/Headmast/tinyAI
**Branch:** 20task
**Description:** tinyAI - A minimal AI agent implementation

### Statistics
- **Total Files:** 45
- **Java Files:** 23
- **Python Files:** 12
- **JavaScript/TypeScript Files:** 5

### Technologies
- **Languages:** Java, Python
- **Build Tools:** Maven, pip

### Dependencies
spring-boot-starter-web, spring-ai-core, jackson-databind

### Overview
A minimal AI agent implementation using Spring Boot and LangChain

### Analysis Date
2026-04-13 14:30:22

### Task Context
Day 20 task repository comparison - AI Advent Challenge
```

### Querying Saved Knowledge

After running the test, you can query the saved knowledge:

**Via Knowledge MCP directly:**
```bash
# List all knowledge
curl -s -X POST http://localhost:8082/mcp/tools/knowledge_contents \
  -H "Content-Type: application/json" \
  -d '{"regex": "Day 20"}'

# Find specific repository knowledge
curl -s -X POST http://localhost:8082/mcp/tools/find_knowledge \
  -H "Content-Type: application/json" \
  -d '{"regex": "tinyAI"}'
```

**Via AIChat web interface:**
```
/mcp_github_list          # List GitHub tools
"What do you know about tinyAI?"              # Query knowledge
"Show me the comparison of Day 20 repositories"  # Find comparison
```

## Using GitHub MCP Tools

The test demonstrates these GitHub MCP tools:

### 1. clone_repository
```bash
curl -X POST http://localhost:8083/mcp/tools/clone_repository \
  -H "Content-Type: application/json" \
  -d '{"repo_url": "https://github.com/Headmast/tinyAI", "branch": "20task"}'
```

### 2. get_repo_structure
```bash
curl -X POST http://localhost:8083/mcp/tools/get_repo_structure \
  -H "Content-Type: application/json" \
  -d '{"repo_path": "github-repos/tinyAI", "max_depth": "2"}'
```

### 3. read_file_contents
```bash
curl -X POST http://localhost:8083/mcp/tools/read_file_contents \
  -H "Content-Type: application/json" \
  -d '{"repo_path": "github-repos/tinyAI", "file_path": "README.md"}'
```

### 4. list_issues
```bash
curl -X POST http://localhost:8083/mcp/tools/list_issues \
  -H "Content-Type: application/json" \
  -d '{"repo_full_name": "Headmast/tinyAI", "state": "open"}'
```

### 5. search_repositories
```bash
curl -X POST http://localhost:8083/mcp/tools/search_repositories \
  -H "Content-Type: application/json" \
  -d '{"query": "AI agent Spring Boot", "language": "java", "sort": "stars"}'
```

## Using Knowledge MCP Tools

### 1. save_knowledge
```bash
curl -X POST http://localhost:8082/mcp/tools/save_knowledge \
  -H "Content-Type: application/json" \
  -d '{
    "title": "How to clone and analyze GitHub repos",
    "description": "Use GitHub MCP clone_repository tool, then analyze structure..."
  }'
```

### 2. knowledge_contents
```bash
# List all knowledge
curl -X POST http://localhost:8082/mcp/tools/knowledge_contents \
  -H "Content-Type: application/json" \
  -d '{}'

# Filter by regex
curl -X POST http://localhost:8082/mcp/tools/knowledge_contents \
  -H "Content-Type: application/json" \
  -d '{"regex": "Day 20"}'
```

### 3. find_knowledge
```bash
curl -X POST http://localhost:8082/mcp/tools/find_knowledge \
  -H "Content-Type: application/json" \
  -d '{"regex": "tinyAI"}'
```

## Natural Language Usage with AIChat

Once the knowledge is saved, you can use natural language queries:

### GitHub Operations
- "Clone the spring-projects/spring-boot repository"
- "Show me the structure of tinyAI"
- "What are the main dependencies in agent_challenge?"
- "List open issues in Headmast/tinyAI"
- "Search for Python AI repositories"

### Knowledge Queries
- "How do I analyze GitHub repositories?"
- "What do you know about tinyAI?"
- "Show me Day 20 task information"
- "Compare the three repositories we analyzed"

## Customizing the Test

### Adding More Repositories

Edit the script and modify these arrays:

```bash
declare -a REPO_URLS=(
    "https://github.com/Headmast/tinyAI"
    "https://github.com/DieOfCode/agent_challenge"
    # Add more here...
)

declare -a REPO_NAMES=(
    "Headmast/tinyAI"
    "DieOfCode/agent_challenge"
    # Add more here...
)

declare -a REPO_BRANCHS=(
    "20task"
    "codex/day20"
    # Add more here...
)
```

### Changing Analysis Depth

Modify the `find` commands to include more files:
```bash
# Current: head -50 (shows 50 files)
find . -type f -not -path '*/\.*' | head -50

# Deeper: show all files
find . -type f -not -path '*/\.*'
```

### Custom Knowledge Format

Edit the `knowledge_description` variable in the script to change what gets saved:

```bash
knowledge_description="## Custom Format
**Repository:** $repo_name
**My Analysis:** ..."
```

## Troubleshooting

### GitHub MCP Connection Failed
```bash
# Check if token is set
echo $GITHUB_TOKEN

# Verify server is running
curl http://localhost:8083

# Check logs
cd mcp-github && mvn spring-boot:run
```

### Knowledge Not Saving
```bash
# Test knowledge MCP directly
curl -X POST http://localhost:8082/mcp/tools/knowledge_contents \
  -H "Content-Type: application/json" \
  -d '{}'

# Check knowledge database
cd mcp-knowledge
sqlite3 knowledge.db "SELECT title FROM knowledge;"
```

### Script Fails on Clone
```bash
# Check git is installed
git --version

# Try manual clone
cd github-repos
git clone https://github.com/Headmast/tinyAI

# Verify token permissions
# Token needs 'repo' scope for private repos
```

## Expected Output Files

After running the test, you'll have:

```
/Users/paveltitov/Documents/programming/ai_challenge/
├── github-repos/
│   ├── tinyAI/
│   ├── agent_challenge/
│   └── ai-advent-challenge-tasks/
├── knowledge_backup_*.md (backup files if MCP save fails)
└── repository_comparison-report-YYYYMMDD_HHMMSS.md
```

## Advanced Usage

### Batch Processing Multiple Tests

Create a wrapper script to run multiple tests:

```bash
#!/bin/bash
# run_all_tests.sh

for task_day in {1..20}; do
    echo "Running Day $task_day test..."
    ./test_github_knowledge_mcp.sh
done
```

### Scheduled Analysis

Use cron to run periodically:
```bash
# Run every day at 9 AM
0 9 * * * cd /path/to/ai_challenge && ./test_github_knowledge_mcp.sh >> test.log 2>&1
```

## Next Steps

1. **Extend the script** to analyze code quality metrics
2. **Add more MCP tools** like creating issues or PRs
3. **Integrate with CI/CD** to run on commits
4. **Build dashboards** to visualize repository comparisons
5. **Add AI-powered insights** using the knowledge saved

## Support

For issues or questions:
- Check GITHUB_MCP_INTEGRATION.md for GitHub MCP details
- Check mcp-knowledge/README.md for knowledge MCP details
- Review server logs for error messages
