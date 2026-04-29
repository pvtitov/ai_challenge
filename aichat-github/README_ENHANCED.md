# Aichat-GitHub - Enhanced GitHub Project Analyzer and Code Reviewer

## 🎯 What This Does

An AI-powered assistant that learns about GitHub projects and provides intelligent code reviews using:
- **RAG (Retrieval-Augmented Generation)** - Project documentation and guidelines
- **GitHub MCP (Model Context Protocol)** - Real-time repository data
- **LLM Analysis** - Intelligent code review and explanation

## ✨ New Features

### 1. Enhanced RAG Integration (FIXED)
- **RAG is now HIGHEST priority** knowledge source
- System prompt explicitly instructs LLM to use RAG first
- Better context formatting for improved comprehension
- LLM must cite RAG sources when answering

**Test it:**
```
You: What is this project about?
→ Should show: "[RAG: Found X relevant source(s) in knowledge base]"
→ LLM will use project documentation from embeddings.db
```

### 2. Improved GitHub MCP Support (FIXED)
- **Better keyword detection** for commits, branches, files, structure
- **Richer context data** passed to LLM
- **Support for more operations**: README, repository structure, file contents
- **Clearer instructions** for LLM to use MCP data

**Test it:**
```
You: Show last commit
→ Should show: "[GitHub MCP: ✓ Server available]"
→ Displays actual commits from repository

You: What branches exist?
→ Shows all available branches

You: Show repository structure
→ Displays file tree
```

### 3. Code Review Mode (NEW)
Use `/review` command to analyze code:

```bash
/review last commit          # Review most recent commit
/review PR #123              # Review specific pull request
/review main branch          # Review main branch
/review entire project       # Comprehensive project review
/review file Main.java       # Review specific file
```

**Review Process:**
1. Gathers project context from RAG (docs, guidelines, architecture)
2. Fetches review target from GitHub MCP (commits, PRs, files)
3. LLM analyzes for bugs, security, architecture, best practices
4. Structured review output with severity labels and recommendations

### 4. Enhanced System Prompts
- **Priority-based knowledge**: RAG → GitHub MCP → General knowledge
- **Explicit citations** required
- **Better context organization**
- **Code review mode** with structured process

## 🚀 Quick Start

### Prerequisites
1. **Ollama** running: `ollama serve`
2. **Embedding model**: `ollama pull nomic-embed-text`
3. **Chat model**: `ollama pull llama3.2:1b` (or larger)
4. **MCP Server** running on port 8083
5. **Embeddings database** at `private/tinyAI/embeddings.db`

### Run the Application
```bash
cd aichat-github
./run.sh
```

### Try These Examples

#### Learn About Project
```
You: What is the tinyAI project about?
→ Uses RAG knowledge base FIRST
→ LLM cites project documentation

You: Show me the README
→ Fetches via GitHub MCP
→ Displays content
```

#### Explore Repository
```
You: Show last commit
→ Fetches recent commits via MCP
→ Shows hash, author, date, message

You: What's the repository structure?
→ Gets file tree via MCP
→ Explains architecture

You: Show branches
→ Lists all branches
→ Shows branch info
```

#### Code Review
```
/review last commit
→ Analyzes recent changes
→ Identifies issues

/review entire project
→ Comprehensive review
→ Architecture analysis

/review file llm_cli.py
→ Reviews specific file
→ Code quality check
```

## 📊 How It Works

### Data Flow
```
User Query
    ↓
1. RAG Search (embeddings.db)
   → Project documentation context
   → Architecture guidelines
   → Code style guides
    ↓
2. GitHub MCP (mcp-github server)
   → Repository structure
   → Commits, branches, PRs
   → File contents
    ↓
3. Enhanced System Prompt
   → RAG context (HIGHEST priority)
   → GitHub context (SECOND priority)
   → Explicit usage instructions
    ↓
4. LLM Response
   → Uses provided context
   → Cites sources
   → Structured output
```

### RAG Priority System
The enhanced system prompt instructs the LLM to use knowledge sources in this order:

1. **RAG Knowledge Base** (HIGHEST)
   - Project documentation
   - Architecture guidelines
   - Code style guides
   - Requirements

2. **GitHub MCP Data** (SECOND)
   - Actual code files
   - Commits and changes
   - Repository structure

3. **General Knowledge** (FALLBACK)
   - Only when RAG/MCP data unavailable

## 🔧 Configuration

### ApiConstants.java
```java
// RAG Configuration
EMBEDDING_DB_PATH = "{user.dir}/private/tinyAI/embeddings.db"
OLLAMA_URL = "http://localhost:11434"
OLLAMA_MODEL = "nomic-embed-text"
similarityThreshold = 0.7
topK = 5

// MCP Configuration  
GITHUB_MCP_SERVER_URL = "http://localhost:8083"
TINYAI_REPO_URL = "https://github.com/Headmast/tinyAI.git"
TINYAI_REPO_PATH = "{user.dir}/private/tinyAI"
```

### Available Commands
```
/quit                      - Exit application
/clean                     - Clear dialog history
/model                     - List available models
/model <name>              - Switch model
/review <target>           - Code review mode
/help                      - Show help
/help <question>           - Ask about project
```

## 📝 Current Capabilities

### ✅ What Works Now
- **RAG Search**: 35 indexed chunks from tinyAI project
- **GitHub MCP**: 11 tools (commits, branches, files, structure, PRs, etc.)
- **Code Review**: Structured review workflow
- **Enhanced Prompts**: RAG-first priority
- **Source Citations**: LLM must cite sources
- **Multi-model Support**: Switch between Ollama models

### ⚠️ Current Limitations
- **Custom MCP Server**: Only 11 tools (missing diff viewing, commit comparison)
- **Hardcoded Repo**: Currently set to tinyAI only
- **Manual Embeddings**: Must run embedding-tool manually
- **No Dynamic Branch Switching**: Can't `/checkout` branches yet

## 🚀 Recommended: Official GitHub MCP Server

### Why Switch?
The official [github/github-mcp-server](https://github.com/github/github-mcp-server) provides:
- **50+ tools** vs current 11
- **Diff viewing** between commits/branches
- **Full PR review** with diffs
- **Code search** across repos
- **Official support** and updates
- **Better security**

### How to Switch
1. **Install official server**:
```bash
# Docker (recommended)
docker run -e GITHUB_PERSONAL_ACCESS_TOKEN=your_token \
  ghcr.io/github/github-mcp-server:latest

# Or build from source
git clone https://github.com/github/github-mcp-server.git
cd github-mcp-server
go build ./cmd/github-mcp-server
```

2. **Update configuration**:
```java
// ApiConstants.java
public static final String GITHUB_MCP_SERVER_URL = "http://localhost:3000";
```

3. **Update MCP client** to match official API (see ENHANCEMENT_GUIDE.md)

## 🧪 Testing Your Setup

### Verify RAG
```bash
# Check embeddings
sqlite3 private/tinyAI/embeddings.db "SELECT COUNT(*) FROM embedding_index;"
# Should return: 35

# Test in app
You: What is this project about?
# Should show: "[RAG: Found X relevant source(s)]"
```

### Verify MCP
```bash
# Check server
curl http://localhost:8083/mcp/message

# Test in app  
You: Show last commit
# Should show: "[GitHub MCP: ✓ Server available]"
```

### Test Code Review
```bash
/review entire project
# Should gather RAG context, then MCP data, then review
```

## 📚 Documentation

- [ENHANCEMENT_GUIDE.md](ENHANCEMENT_GUIDE.md) - Complete enhancement guide
- [MCP_SETUP_GUIDE.md](MCP_SETUP_GUIDE.md) - MCP server setup
- [RAG_ENHANCEMENTS.md](RAG_ENHANCEMENTS.md) - RAG system details

## 🛠️ Architecture

### Components
```
aichat-github/           # Main chat client (Java)
├── service/
│   ├── ChatServiceImpl.java      # Main orchestration (ENHANCED)
│   ├── CodeReviewService.java    # Code review workflow (NEW)
│   ├── EmbeddingSearchService    # RAG search
│   └── GitHubMcpService.java     # MCP client (ENHANCED)
├── constants/
│   └── ApiConstants.java         # System prompts (ENHANCED)
└── repository/
    └── EmbeddingRepository.java  # RAG database

mcp-github/              # Custom MCP server (11 tools)
embedding-tool/          # Embedding generator

private/tinyAI/
└── embeddings.db        # RAG knowledge base (35 chunks)
```

## 🎯 Future Enhancements

### Phase 2: Interactive Features
- [ ] `/checkout <branch>` - Switch branches
- [ ] `/diff <commit1> <commit2>` - View diffs
- [ ] `/clone <repo-url>` - Clone any repo
- [ ] Dynamic repo access

### Phase 3: Advanced Review
- [ ] Full PR review with diffs
- [ ] Commit range review
- [ ] Branch comparison
- [ ] Architecture analysis

### Phase 4: Automation
- [ ] Auto-update embeddings
- [ ] Multi-repo support
- [ ] Approximate nearest neighbor search
- [ ] PR creation from chat

## 🐛 Troubleshooting

### RAG Not Working
1. Check embeddings.db exists
2. Verify Ollama running: `ollama serve`
3. Check model: `ollama list | grep nomic-embed-text`
4. Look for "[RAG: Found X]" messages

### MCP Not Working  
1. Check server: `curl http://localhost:8083/mcp/message`
2. Verify `GITHUB_TOKEN` is set
3. Check port 8083 not blocked
4. Look for "[GitHub MCP: ✓]" messages

### LLM Not Using Context
1. Try more specific questions
2. Check RAG/MCP data is fetched (watch status messages)
3. Use larger model (llama3.2:3b or bigger)
4. Context is provided but LLM may still use general knowledge

## 📄 License

MIT License

## 🤝 Contributing

Enhancements welcome! See ENHANCEMENT_GUIDE.md for improvement ideas.
