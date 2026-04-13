# GitHub MCP Server

A Spring Boot MCP (Model Context Protocol) server that provides natural language access to GitHub operations. This server follows the same pattern as `mcp-knowledge` for seamless integration with the AIChat application.

## Features

- **Repository Cloning**: Clone any GitHub repository locally
- **Repository Exploration**: Browse file structure and read file contents
- **Issues Management**: List and track issues in any repository
- **Pull Requests**: List, view details, and create pull requests
- **Code Search**: Search repositories across GitHub
- **Git Operations**: Create branches, commit, and push changes
- **README Access**: Quick project overview and setup instructions

## Prerequisites

- Java 17+
- Maven 3.6+
- GitHub Personal Access Token (PAT)

## Setup

### 1. Generate GitHub Token

1. Go to https://github.com/settings/tokens?type=beta
2. Create a fine-grained token with these scopes:
   - `repo` (full control of private repositories)
   - `read:org` (organization access)
3. Copy the token

### 2. Set Environment Variable

```bash
export GITHUB_TOKEN=your_personal_access_token_here
```

### 3. Build and Run

```bash
# Build
mvn clean package

# Run
mvn spring-boot:run
```

The server starts on **port 8083** with SSE endpoint at `/mcp/message`.

## Available Tools

| Tool | Description | Parameters |
|------|-------------|------------|
| `clone_repository` | Clone a GitHub repository | `repo_url`, `branch` (optional) |
| `get_repo_structure` | Get file/directory structure | `repo_path`, `max_depth` (optional) |
| `read_file_contents` | Read a specific file | `repo_path`, `file_path` |
| `list_issues` | List repository issues | `repo_full_name`, `state` (optional) |
| `list_pull_requests` | List pull requests | `repo_full_name`, `state` (optional) |
| `get_pull_request_details` | Get PR details | `repo_full_name`, `pr_number` |
| `create_pull_request` | Create a new PR | `repo_full_name`, `title`, `head`, `base`, `body` (optional) |
| `search_repositories` | Search repositories | `query`, `language` (optional), `sort` (optional) |
| `get_readme` | Get README content | `repo_full_name` |
| `commit_and_push` | Commit and push changes | `repo_path`, `commit_message`, `file_path` |
| `create_branch` | Create a new branch | `repo_path`, `branch_name` |

## Usage with AIChat

### 1. Start the Server

```bash
export GITHUB_TOKEN=your_token_here
mvn spring-boot:run
```

### 2. Connect from AIChat

In the AIChat web interface (http://localhost:8080), use these commands:

```
/mcp_github_connect   - Connect to GitHub MCP server
/mcp_github_status   - Check connection status
/mcp_github_list     - List available tools
```

### 3. Use Natural Language

Once connected, you can use natural language prompts:

- "Clone the spring-projects/spring-boot repository"
- "Show me the structure of spring-boot"
- "List open issues in microsoft/vscode"
- "Search for Python machine learning repositories"
- "Create a pull request titled 'Fix bug' from feature-branch to main"

## Architecture

```
┌──────────────────┐
│  GitHub MCP      │
│  Spring Server   │
│  (Port 8083)     │
│                  │
│  ┌────────────┐  │
│  │GitHubService│ │
│  │  - JGit    │  │
│  │  - github- │  │
│  │    api     │  │
│  └─────┬──────┘  │
└────────┼─────────┘
         │
  ┌──────┴──────┐
  │             │
Local Git    GitHub API
Operations   (REST API)
  (JGit)
```

## Dependencies

- **Spring Boot 3.2.0**: Application framework
- **MCP SDK 1.1.1**: Model Context Protocol implementation
- **JGit 6.7.0**: Git operations for Java
- **github-api 1.318**: GitHub REST API client

## Project Structure

```
mcp-github/
├── pom.xml
├── test_github_mcp.sh
├── README.md
└── src/main/
    ├── java/com/github/pvtitov/aichat/mcpserver/
    │   ├── McpGitHubServerApplication.java  # Main application
    │   ├── McpServerConfig.java             # MCP server configuration
    │   └── GitHubService.java               # GitHub operations service
    └── resources/
        └── application.properties           # Server configuration
```

## Development

### Adding New Tools

1. Add a new method to `GitHubService.java`
2. Create a tool specification in `McpServerConfig.java`
3. Add to the tools list in the `mcpSyncServer` bean

Example:

```java
// In McpServerConfig.java
McpSchema.Tool myTool = McpSchema.Tool.builder()
    .name("my_new_tool")
    .description("Description of what the tool does")
    .inputSchema(mySchema)
    .build();
    
tools.add(new McpServerFeatures.SyncToolSpecification(myTool, (exchange, request) -> {
    // Implementation
    String result = GitHubService.myNewMethod(...);
    return new McpSchema.CallToolResult(
        List.of(new McpSchema.TextContent(result)),
        false, null, null
    );
}));
```

### Testing

```bash
# Quick test (builds and runs)
./test_github_mcp.sh

# Or manually
mvn clean package
java -jar target/mcp-github-1.0.jar
```

## Troubleshooting

### Connection Issues

- **Token not set**: Ensure `GITHUB_TOKEN` environment variable is set
- **Port conflict**: Check if port 8083 is already in use (`lsof -i :8083`)
- **Build errors**: Run `mvn clean compile` to see detailed errors

### GitHub API Errors

- **401 Unauthorized**: Token is invalid or expired
- **403 Forbidden**: Token lacks required permissions
- **404 Not Found**: Repository doesn't exist or no access
- **Rate limiting**: GitHub API limit reached (5000 req/hour)

## License

This project is part of the AIChat MCP integration suite.
