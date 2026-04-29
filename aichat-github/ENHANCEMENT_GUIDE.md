# Aichat-GitHub Enhancement Guide

## Overview
This guide covers the enhanced `aichat-github` project with improved RAG integration, GitHub MCP support, and code review capabilities.

## Key Improvements

### 1. Enhanced RAG Integration (First Priority)
- **RAG is now the HIGHEST priority knowledge source**
- System prompt explicitly instructs LLM to use RAG first
- LLM must cite RAG sources when answering project-specific questions
- Better context formatting for improved LLM comprehension

### 2. Improved GitHub MCP Integration
- **Better keyword detection** for commits, branches, files, structure
- **Richer context data** passed to LLM (includes authors, dates, repo URL)
- **Support for more operations**: README, repository structure, file contents
- **Clearer instructions** for LLM to use MCP data for factual answers

### 3. Enhanced System Prompts
- **Priority-based knowledge sources**: RAG → GitHub MCP → General knowledge
- **Explicit instructions** to cite sources
- **Better context organization** with clear section headers
- **Code review mode** with structured review process

## Current Architecture

### Components
1. **aichat-github** - Java CLI chat client (this project)
2. **mcp-github** - Custom Spring Boot MCP server (11 tools)
3. **embedding-tool** - Embedding generation CLI tool
4. **private/tinyAI/embeddings.db** - RAG knowledge base (35 chunks)

### Data Flow
```
User Query
    ↓
1. RAG Search (embeddings.db) → Project documentation context
    ↓
2. GitHub MCP (mcp-github) → Repository data context
    ↓
3. Enhanced System Prompt → LLM with prioritized context
    ↓
4. Structured Response with cited sources
```

## Usage Examples

### Learn About Project
```
You: What is this project about?
→ RAG searches embeddings for project overview
→ MCP fetches README.md
→ LLM combines both sources with citations

You: Show the repository structure
→ MCP fetches repo structure
→ LLM explains architecture
```

### View Commits and Branches
```
You: Show last commit
→ MCP fetches recent commits
→ LLM displays with specific hashes and messages

You: What branches exist?
→ MCP fetches branch list
→ LLM shows available branches

You: Show commit abc1234
→ MCP can fetch specific commit (if hash detected)
```

### Read Files
```
You: Show me the README
→ MCP reads README.md
→ LLM displays content

You: What's in requirements.txt?
→ MCP reads the file
→ LLM shows dependencies
```

### Code Review (Enhanced)
```
You: Review this project
→ RAG fetches project docs and guidelines
→ MCP gets repository structure
→ LLM analyzes architecture and provides review

You: Review the last commit
→ MCP fetches commit details
→ LLM analyzes changes for bugs, quality, etc.
```

## Current Limitations

### Custom MCP Server (mcp-github)
The current custom MCP server has **11 tools**:
- ✅ clone_repository
- ✅ get_repo_structure
- ✅ read_file_contents
- ✅ list_issues
- ✅ list_pull_requests
- ✅ get_pull_request_details
- ✅ create_pull_request
- ✅ search_repositories
- ✅ get_readme
- ✅ commit_and_push
- ✅ create_branch

**Missing**: Direct diff viewing, commit-specific lookups, branch comparison

### RAG System
- ✅ Works with 35 indexed chunks from tinyAI project
- ✅ Cosine similarity search with threshold
- ⚠️ Only indexes files you manually process with `embedding-tool`
- ⚠️ No automatic updates when project changes

## Recommended: Switch to Official GitHub MCP Server

### Why Use Official Server?
The official [github/github-mcp-server](https://github.com/github/github-mcp-server) provides:
- **50+ tools** vs current 11 tools
- **Diff viewing** between commits/branches
- **PR review** with full diff analysis
- **Code search** across repositories
- **Actions/CI/CD** integration access
- **Official support** and continuous updates
- **Better security** (read-only mode, scoped permissions)

### How to Switch

1. **Install Official MCP Server**:
```bash
# Using Docker (recommended)
docker run -e GITHUB_PERSONAL_ACCESS_TOKEN=your_token \
  ghcr.io/github/github-mcp-server:latest

# Or build from source
git clone https://github.com/github/github-mcp-server.git
cd github-mcp-server
go build ./cmd/github-mcp-server
./github-mcp-server
```

2. **Update Configuration**:
Edit `ApiConstants.java`:
```java
// Change to official server port/URL
public static final String GITHUB_MCP_SERVER_URL = "http://localhost:3000";
```

3. **Update MCP Client**:
The official server uses different tool names and parameters.
You'll need to update `GitHubMcpService.java` to match the official API.

## Testing Your Setup

### 1. Verify RAG is Working
```bash
# Check embeddings database
sqlite3 private/tinyAI/embeddings.db "SELECT COUNT(*) FROM embedding_index;"
# Should return: 35

# Run the app and ask project-specific questions
# Should show: "[RAG: Found X relevant source(s) in knowledge base]"
```

### 2. Verify MCP is Working
```bash
# Check MCP server is running
curl http://localhost:8083/mcp/message

# Run the app and ask about commits/branches
# Should show: "[GitHub MCP: ✓ Server available]"
```

### 3. Test Enhanced Features
```
You: What is the tinyAI project about?
→ Should use RAG context FIRST

You: Show last commit
→ Should use MCP data and show actual commits

You: Show repository structure
→ Should display file tree via MCP

You: Review this project
→ Should combine RAG + MCP for comprehensive review
```

## Future Enhancements (Planned)

### Phase 2: Interactive Capabilities
- [ ] `/checkout <branch>` command to switch branches
- [ ] `/diff <commit1> <commit2>` to view diffs
- [ ] `/clone <repo-url>` to clone any GitHub repo
- [ ] Dynamic repo access (not just hardcoded tinyAI)

### Phase 3: Code Review System
- [ ] "Review PR #123" workflow
- [ ] "Review commit abc123" analysis
- [ ] Structured review output with severity labels
- [ ] Architecture analysis using RAG knowledge

### Phase 4: Advanced Features
- [ ] Automatic embedding updates when project changes
- [ ] Approximate nearest neighbor search for faster RAG
- [ ] Multi-repo knowledge base
- [ ] PR creation from chat

## Troubleshooting

### RAG Not Working
1. Check embeddings.db exists: `ls -la private/tinyAI/embeddings.db`
2. Verify Ollama is running: `ollama serve`
3. Check embedding model: `ollama list | grep nomic-embed-text`
4. Test embedding generation: `curl http://localhost:11434/api/embeddings`

### MCP Not Working
1. Check MCP server is running: `curl http://localhost:8083/mcp/message`
2. Verify GITHUB_TOKEN is set: `echo $GITHUB_TOKEN`
3. Check server logs for errors
4. Ensure port 8083 is not blocked

### LLM Not Using Context
1. System prompts are enhanced but LLM may still ignore them
2. Try more specific questions that require context
3. Check that RAG/MCP data is actually being fetched (look for status messages)
4. Use models with better instruction-following (llama3.2:3b or larger)

## Configuration Reference

### ApiConstants.java
```java
// RAG Configuration
EMBEDDING_DB_PATH = "{user.dir}/private/tinyAI/embeddings.db"
OLLAMA_URL = "http://localhost:11434"
OLLAMA_MODEL = "nomic-embed-text"

// MCP Configuration
GITHUB_MCP_SERVER_URL = "http://localhost:8083"
TINYAI_REPO_URL = "https://github.com/Headmast/tinyAI.git"
TINYAI_REPO_PATH = "{user.dir}/private/tinyAI"

// Search Configuration
// (in EmbeddingSearchService)
topK = 5
similarityThreshold = 0.7
```

## Contributing

To enhance further:
1. Add more MCP tools for diff viewing
2. Implement branch switching
3. Add automatic embedding updates
4. Create PR review workflow
5. Add code quality metrics

## Resources

- [Official GitHub MCP Server](https://github.com/github/github-mcp-server)
- [MCP Protocol Documentation](https://modelcontextprotocol.io)
- [Ollama Documentation](https://ollama.ai)
- [Embedding Tool](../embedding-tool/README.md)
