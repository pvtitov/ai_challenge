# GitHub MCP Server Guide

## Automated Setup (Recommended)

Just run the application using the provided script:

```bash
./run.sh
```

This script will automatically:
1. ✅ Build the GitHub MCP server (`mcp-github/`)
2. ✅ Start the MCP server on port 8083
3. ✅ Build aichat-github
4. ✅ Start the aichat-github client
5. ✅ **Clean up**: Stop the MCP server when you exit the client

**When you exit the client (Ctrl+C or /quit), the MCP server is automatically stopped.**

## Manual Setup

If you prefer to run the MCP server manually:

### Start MCP Server

```bash
cd /Users/paveltitov/Documents/programming/ai_challenge/mcp-github
mvn spring-boot:run
```

The server will start on **port 8083**.

### Start aichat-github Client

In a separate terminal:

```bash
cd /Users/paveltitov/Documents/programming/ai_challenge/aichat-github
./run.sh
```

### Stop MCP Server

When done, press **Ctrl+C** in the MCP server terminal.

## Configuration

The MCP server URL is configured in:
- `aichat-github/src/main/java/com/github/pvtitov/aichatgithub/constants/ApiConstants.java`
- Default: `http://localhost:8083`

## What the MCP Server Provides

When connected, the aichat-github agent can:
- **List commits**: "Show me recent commits"
- **View branches**: "What branches are available?"
- **Read files**: "Show me the README.md"
- **Search commits**: "Show commits about MCP"
- **View specific commits**: "What changed in commit abc123?"

## Troubleshooting

### MCP Server fails to start
- Check if port 8083 is already in use: `lsof -i :8083`
- Kill any process using that port: `kill -9 <PID>`

### GitHub token required
Some GitHub operations may require authentication. If needed:
```bash
export GITHUB_TOKEN=your_personal_access_token_here
```

### Ollama not running
Make sure Ollama is running:
```bash
ollama serve
```

Pull required models:
```bash
ollama pull llama3.2:1b
ollama pull nomic-embed-text
```
