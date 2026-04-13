# GitHub MCP Server Integration with AIChat

This guide explains how to use the custom GitHub MCP server integrated with your AIChat web client, enabling AI-powered interactions with GitHub repositories, issues, pull requests, and more using natural language.

---

## GitHub MCP Server Capabilities (Overview)

The GitHub MCP server provides natural language access to:

- **Repository Cloning**: Clone any GitHub repository with a simple command
- **Repository Exploration**: Browse file structure and read file contents
- **Issues Management**: List and track issues in any repository
- **Pull Requests**: List, view details, and create pull requests
- **Code Search**: Search repositories across GitHub
- **Git Operations**: Create branches, commit, and push changes
- **README Access**: Quick project overview and setup instructions

**Key Difference from Official GitHub MCP Server**:
- This is a **custom Spring Boot MCP server** (following the `mcp-knowledge` pattern)
- No Docker or wrapper needed - runs directly as a Java application
- Uses **JGit** for local Git operations and **github-api** for GitHub API calls
- Accessible via natural language prompts in your chat

---

## Prerequisites

1. **GitHub Personal Access Token (PAT)** with scopes:
   - `repo` (full control of private repositories)
   - `read:org` (organization access, if needed)

2. **Java 17+** installed

3. **Maven** installed

4. **AIChat application** built and ready to run

---

## Step 1: Generate a GitHub Personal Access Token

1. Go to GitHub → **Settings** → **Developer settings** → **Personal access tokens** → **Fine-grained tokens**
   - Direct URL: https://github.com/settings/tokens?type=beta
2. Click **Generate new token**
3. Set:
   - **Token name**: `aichat-mcp` (or any name)
   - **Expiration**: Choose duration (recommend 90 days)
   - **Repository access**: Select "All repositories" or specific repos
   - **Permissions**:
     - **Repository permissions**:
       - Contents: Read and Write
       - Issues: Read/Write
       - Pull requests: Read/Write
       - Metadata: Read-only
4. Click **Generate token**
5. **Copy the token immediately** — you won't see it again!

---

## Step 2: Set Environment Variable

Before starting the GitHub MCP server, set your GitHub token:

```bash
export GITHUB_TOKEN=your_personal_access_token_here
```

**Important**: This must be set in the same terminal session where you run the server.

---

## Step 3: Start the GitHub MCP Server

```bash
cd /Users/paveltitov/Documents/programming/ai_challenge/mcp-github
mvn spring-boot:run
```

The server will start on **port 8083** with SSE endpoint at `/mcp/message`.

---

## Step 4: Configure AIChat for GitHub MCP Server

The configuration is already set up in:

**`aichat/src/main/resources/application.properties`**:
```properties
mcp.github.server.url=http://localhost:8083
```

**`McpService.java`** and **`McpServiceImpl.java`** already include:
- `initializeGitHubConnection()`
- `isGitHubConnected()`
- `callGitHubTool()`

---

## Step 5: Use GitHub MCP in AIChat Web Client

### 5.1 Start All Servers

In separate terminals (or use `run_servers.sh`):

**Terminal 1 - MCP Test Server (port 8081)**:
```bash
cd mcp-test-server
mvn spring-boot:run
```

**Terminal 2 - MCP Knowledge Server (port 8082)**:
```bash
cd mcp-knowledge
mvn spring-boot:run
```

**Terminal 3 - MCP GitHub Server (port 8083)**:
```bash
export GITHUB_TOKEN=your_token_here
cd mcp-github
mvn spring-boot:run
```

**Terminal 4 - AIChat Application (port 8080)**:
```bash
cd aichat
mvn spring-boot:run
```

### 5.2 Open Web Client

Navigate to: **http://localhost:8080**

### 5.3 Connect to GitHub MCP Server

In the chat input, type:

```
/mcp_github_connect
```

You should see: `GitHub MCP connection established successfully.`

### 5.4 Check Connection Status

```
/mcp_github_status
```

Expected response: `GitHub MCP Connection Status: ACTIVE`

### 5.5 List Available GitHub Tools

```
/mcp_github_list
```

This will show all 11 available GitHub tools with descriptions.

---

## Available GitHub MCP Tools

| Tool Name | Description | Example Usage |
|-----------|-------------|---------------|
| `clone_repository` | Clone a GitHub repository | "Clone the spring-boot repo" |
| `get_repo_structure` | Get file/directory structure | "Show me the structure of spring-boot" |
| `read_file_contents` | Read a specific file | "Read src/main/java/App.java" |
| `list_issues` | List repository issues | "List open issues in spring-projects/spring-boot" |
| `list_pull_requests` | List pull requests | "Show open PRs in microsoft/vscode" |
| `get_pull_request_details` | Get PR details | "Show details of PR #123 in owner/repo" |
| `create_pull_request` | Create a new PR | "Create a PR from feature-branch to main" |
| `search_repositories` | Search repositories | "Search for Python machine learning repos" |
| `get_readme` | Get README content | "Show README for torvalds/linux" |
| `commit_and_push` | Commit and push changes | "Commit my changes and push" |
| `create_branch` | Create a new branch | "Create branch feature-x" |

---

## Example Natural Language Prompts

Once connected, you can use natural language to interact with GitHub:

### Repository Cloning & Exploration

**Clone a Repository:**
- "Clone the repository https://github.com/spring-projects/spring-boot"
- "I want to explore the spring-boot codebase, can you clone it?"
- "Get me a copy of github/github-mcp-server"

**Explore Repository Structure:**
- "Show me the structure of the spring-boot repository"
- "What's the file structure of github-repos/spring-projects/spring-boot?"
- "List the top-level directories in spring-boot"

**Read File Contents:**
- "Read the README.md file in spring-boot"
- "Show me the contents of pom.xml from spring-boot"
- "What's in src/main/java/org/springframework/boot/SpringApplication.java?"

### Pull Requests

**List Pull Requests:**
- "List open pull requests for spring-projects/spring-boot"
- "Show me the PRs in microsoft/vscode"
- "What pull requests are open in github/github-mcp-server?"

**View PR Details:**
- "Show me details for PR #1234 in spring-projects/spring-boot"
- "What's the status of PR #5678 in my-org/my-repo?"
- "Tell me about pull request 42 in torvalds/linux"

**Create Pull Requests:**
- "Create a pull request titled 'Fix bug in login' from branch 'fix-login' to 'main' in my-org/my-repo"
- "Open a PR with title 'Add new feature' from 'feature-branch' to 'develop' in owner/repo with description 'This adds...'"

### Issues

**List Issues:**
- "List open issues for kubernetes/kubernetes"
- "Show me the issues in spring-projects/spring-boot"
- "What bugs are reported in electron/electron?"

### Code Search

**Search Repositories:**
- "Search for Python machine learning repositories"
- "Find Java Spring Boot repositories sorted by stars"
- "Search for repositories with 'docker compose' in the name"

### Git Operations

**Create Branch:**
- "Create a new branch called 'feature-auth' in my local repo"
- "Switch to a new branch 'bugfix-123' in github-repos/my-org/my-repo"

**Commit and Push:**
- "Commit my changes with message 'Fixed login bug' and push"
- "Commit the changes to pom.xml and push to remote"

### README & Documentation

**Get README:**
- "Show me the README for spring-projects/spring-boot"
- "What does the github/github-mcp-server project do?"
- "Get the README for torvalds/linux"

---

## Architecture Diagram

```
┌─────────────┐         ┌──────────────────┐         ┌──────────────────┐
│   Browser   │◄───────►│   AIChat Server  │◄───────►│  GitHub MCP      │
│  (Web UI)   │  HTTP   │   (Port 8080)    │   SSE   │  Spring Server   │
└─────────────┘         └──────────────────┘         │   (Port 8083)    │
                                                      │                  │
                                                      │  ┌────────────┐  │
                                                      │  │ GitHubService│ │
                                                      │  │  - JGit    │  │
                                                      │  │  - github- │  │
                                                      │  │    api     │  │
                                                      │  └─────┬──────┘  │
                                                      └────────┼─────────┘
                                                               │
                                                    ┌──────────┴──────────┐
                                                    │                     │
                                               Local Git              GitHub API
                                             Operations (JGit)       (REST API)
```

---

## Troubleshooting

### Connection Issues

1. **Verify token is set**: `echo $GITHUB_TOKEN` should show your token
2. **Check server is running**: Visit http://localhost:8083/mcp in browser
3. **Verify port availability**: `lsof -i :8083`
4. **Check logs**: Review both aichat and mcp-github server logs

### GitHub API Errors

1. **401 Unauthorized**: Token is invalid or expired - regenerate your PAT
2. **403 Forbidden**: Token lacks required scopes - check permissions
3. **404 Not Found**: Repository doesn't exist or no access
4. **Rate Limiting**: GitHub API limit reached (5000 req/hour authenticated)

### Git Operation Errors

1. **Repository not cloned**: Clone it first using `clone_repository` tool
2. **Branch exists**: Try a different branch name
3. **Push rejected**: Check you have write access to the repo
4. **Not a git repo**: Ensure path points to a valid cloned repository

### Tools Not Appearing

1. Run `/mcp_github_connect` first
2. Ensure the mcp-github server is running on port 8083
3. Verify `application.properties` has `mcp.github.server.url=http://localhost:8083`

---

## Security Best Practices

1. **Never commit your PAT** to version control
2. **Use environment variables** or `.env` files (add to `.gitignore`)
3. **Set appropriate token expiration** (90 days recommended)
4. **Use minimum required scopes** for your use case
5. **Restrict file permissions** on config files: `chmod 600 .env`
6. **Use read-only token** if you only need to browse repos

---

## Quick Reference Card

| Command | Description |
|---------|-------------|
| `/mcp_github_connect` | Connect to GitHub MCP server |
| `/mcp_github_status` | Check GitHub MCP connection status |
| `/mcp_github_list` | List available GitHub MCP tools |
| "Clone repo X" | Clone a GitHub repository |
| "Show structure of X" | View repository file tree |
| "Read file X" | View file contents |
| "List issues in X" | Show repository issues |
| "List PRs in X" | Show pull requests |
| "Show PR #N in X" | View PR details |
| "Create PR..." | Create new pull request |
| "Search for X" | Search repositories |
| "Create branch X" | Create new git branch |
| "Commit and push" | Save changes to GitHub |

---

## Building the Server

If you need to rebuild the GitHub MCP server:

```bash
cd /Users/paveltitov/Documents/programming/ai_challenge/mcp-github
mvn clean package
```

To run the packaged JAR:

```bash
java -jar target/mcp-github-1.0.jar
```

---

## Adding New GitHub Tools

To add more tools, edit `McpServerConfig.java` in the `mcp-github` project:

1. Add a new method to `GitHubService.java`
2. Create a new `McpSchema.Tool` definition with JSON schema
3. Add a `SyncToolSpecification` to the tools list
4. Restart the server

Example tool specification follows the pattern in `mcp-knowledge` server.

---

## Next Steps

1. **Add more Git operations**: fetch, pull, merge, rebase, etc.
2. **Support for GitHub Actions**: Trigger and monitor workflows
3. **Code review tools**: Comment on PRs, approve/reject changes
4. **Issue management**: Create, update, close issues
5. **File editing**: Edit files directly through the MCP server
6. **Smart context**: AI automatically understands repo context and suggests actions

---

## Comparison: Custom vs Official GitHub MCP Server

| Feature | Custom (this) | Official GitHub MCP |
|---------|---------------|---------------------|
| **Setup** | Simple Java app | Docker + HTTP wrapper |
| **Integration** | Native Spring Boot | stdio transport |
| **Usage** | Natural language in chat | Requires AI agent support |
| **Git Operations** | Local JGit operations | GitHub API only |
| **Extensibility** | Easy to add tools | Requires understanding Go |
| **Dependencies** | JGit, github-api | Docker, Go runtime |
| **Learning Curve** | Low (Java/Spring) | Medium-High |

This custom server is designed for **easy integration** with your AIChat application, following the same pattern as `mcp-knowledge` for consistency.
