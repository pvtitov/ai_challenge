# GitHub MCP + Knowledge MCP Quick Start

## 🚀 Quick Start (5 minutes)

### 1. Set GitHub Token
```bash
export GITHUB_TOKEN=your_personal_access_token_here
```
Get token: https://github.com/settings/tokens?type=beta

### 2. Start All Servers
```bash
./run_servers.sh
```

### 3. Run Tests
```bash
# Full repository analysis test
./test_github_knowledge_mcp.sh

# Interactive demo with AIChat
./demo_aichat_github_mcp.sh
```

---

## 📋 Available Scripts

| Script | Purpose | When to Use |
|--------|---------|-------------|
| `test_github_knowledge_mcp.sh` | Clone, analyze, compare repos & save knowledge | Testing MCP integration |
| `demo_aichat_github_mcp.sh` | Natural language demo with AIChat | Showing capabilities |
| `run_servers.sh` | Start all servers at once | Daily development |

---

## 🎯 What Gets Tested

### GitHub MCP Server (port 8083)
- ✅ Clone repositories
- ✅ Get repository structure
- ✅ Read file contents
- ✅ List issues & pull requests
- ✅ Search repositories
- ✅ Create branches, commits, PRs

### Knowledge MCP Server (port 8082)
- ✅ Save repository analysis
- ✅ List knowledge entries
- ✅ Find knowledge by pattern
- ✅ Query via natural language

### AIChat Integration (port 8080)
- ✅ Natural language GitHub operations
- ✅ Save knowledge from chat
- ✅ Query saved knowledge
- ✅ Complex multi-step workflows

---

## 💡 Example Usage

### Via Test Script
```bash
./test_github_knowledge_mcp.sh
```
**What it does:**
1. Clones 3 repositories using GitHub MCP
2. Analyzes structure & dependencies
3. Compares technologies
4. Saves all findings to Knowledge MCP
5. Generates comparison report

### Via AIChat Web Interface
```
# Connect to GitHub
/mcp_github_connect

# Clone a repo
"Clone https://github.com/Headmast/tinyAI from branch 20task"

# Explore it
"Show me the structure of tinyAI"

# Read files
"What's in the README.md?"

# Save knowledge
"Save this: tinyAI uses Spring Boot with 23 Java files"

# Query later
"What do you know about tinyAI?"
```

### Via API (for automation)
```bash
# Clone repository
curl -X POST http://localhost:8083/mcp/tools/clone_repository \
  -H "Content-Type: application/json" \
  -d '{"repo_url": "https://github.com/Headmast/tinyAI", "branch": "20task"}'

# Get structure
curl -X POST http://localhost:8083/mcp/tools/get_repo_structure \
  -H "Content-Type: application/json" \
  -d '{"repo_path": "github-repos/tinyAI", "max_depth": "2"}'

# Save knowledge
curl -X POST http://localhost:8082/mcp/tools/save_knowledge \
  -H "Content-Type: application/json" \
  -d '{"title": "How to analyze repos", "description": "..."}'
```

---

## 🔍 Repositories Analyzed

| Repository | Branch | Purpose |
|-----------|--------|---------|
| Headmast/tinyAI | 20task | Minimal AI agent |
| DieOfCode/agent_challenge | codex/day20 | Agent challenge implementation |
| fun-bear/ai-advent-challenge-tasks | main | Task descriptions |

---

## 📊 Output Files

After running tests:
```
github-repos/                    # Cloned repositories
├── tinyAI/
├── agent_challenge/
└── ai-advent-challenge-tasks/

repository_comparison-report-*.md  # Detailed comparison
knowledge_backup_*.md              # Knowledge backups
```

---

## 🛠️ Troubleshooting

### Server Not Running
```bash
# Check status
curl http://localhost:8080  # AIChat
curl http://localhost:8082  # Knowledge MCP
curl http://localhost:8083  # GitHub MCP

# Start servers
./run_servers.sh
```

### GitHub Token Issues
```bash
# Verify token
echo $GITHUB_TOKEN

# Test token
curl -H "Authorization: token $GITHUB_TOKEN" https://api.github.com/user
```

### Knowledge Not Saving
```bash
# Test directly
curl -X POST http://localhost:8082/mcp/tools/knowledge_contents \
  -H "Content-Type: application/json" \
  -d '{}'

# Check database
cd mcp-knowledge && sqlite3 knowledge.db "SELECT title FROM knowledge;"
```

---

## 📚 Documentation

- `GITHUB_MCP_INTEGRATION.md` - Complete GitHub MCP guide
- `TEST_GUIDE.md` - Detailed test script documentation
- `mcp-github/README.md` - GitHub MCP server reference
- `mcp-knowledge/` - Knowledge MCP server (example template)

---

## 🎓 Learning Path

1. **Run the demo** → See it in action
   ```bash
   ./demo_aichat_github_mcp.sh
   ```

2. **Run the test** → Understand the analysis
   ```bash
   ./test_github_knowledge_mcp.sh
   ```

3. **Read the guides** → Deep dive
   - `TEST_GUIDE.md`
   - `GITHUB_MCP_INTEGRATION.md`

4. **Try your own repos** → Experiment
   ```bash
   # Edit test_github_knowledge_mcp.sh
   # Add your repositories to REPO_URLS array
   ```

5. **Build on top** → Extend
   - Add new GitHub MCP tools
   - Create custom analysis scripts
   - Integrate with CI/CD

---

## ⚡ One-Liner Commands

```bash
# Quick clone via API
curl -s -X POST http://localhost:8083/mcp/tools/clone_repository \
  -H "Content-Type: application/json" \
  -d '{"repo_url": "https://github.com/spring-projects/spring-boot"}' | python3 -m json.tool

# Quick search
curl -s -X POST http://localhost:8083/mcp/tools/search_repositories \
  -H "Content-Type: application/json" \
  -d '{"query": "AI agent Java", "language": "java"}' | python3 -m json.tool

# List all knowledge
curl -s -X POST http://localhost:8082/mcp/tools/knowledge_contents \
  -H "Content-Type: application/json" \
  -d '{}' | python3 -m json.tool
```

---

## 🎯 Success Criteria

You'll know it's working when:
- ✅ Scripts run without errors
- ✅ Repositories are cloned to `github-repos/`
- ✅ Comparison report is generated
- ✅ Knowledge is saved in `mcp-knowledge/knowledge.db`
- ✅ AIChat responds to natural language GitHub queries

---

## 📞 Need Help?

1. Check server logs
2. Verify environment variables
3. Test endpoints individually
4. Review documentation files
5. Check GitHub token permissions
