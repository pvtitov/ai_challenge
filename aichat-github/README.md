# AI Chat GitHub

A specialized CLI chat agent for discovering and interacting with the private/tinyAI project and other GitHub repositories via MCP (Model Context Protocol).

## Features

- **GitHub MCP Integration**: Access repository code, branches, and commits via MCP server
- **RAG Knowledge Base**: Uses embeddings database (`embeddings.db`) from tinyAI project for context-aware responses
- **Ollama LLM Support**: Uses local Ollama models (default: `llama3.2:1b`)
- **Task Tracking**: Analyzes and tracks tasks from conversations with requirements and verification
- **Help System**: Built-in `/help` command to explore tinyAI project structure and ask questions
- **Full Dialog History**: Saves complete conversation history in SQLite database
- **Token Usage**: Displays token consumption for each response
- **Source Citations**: Shows information sources from RAG and GitHub

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Ollama running locally on `localhost:11434`
  - Required models: `llama3.2:1b` (default), `nomic-embed-text` (for embeddings)
- GitHub MCP server (optional, recommended for full functionality)
- Access to tinyAI project with `embeddings.db`

## Building

```bash
mvn clean package
```

## Running

1. Ensure Ollama is running:
   ```bash
   ollama serve
   ```

2. Pull required models (if not already available):
   ```bash
   ollama pull llama3.2:1b
   ollama pull nomic-embed-text
   ```

3. (Optional) Start GitHub MCP server on `localhost:3000`

4. Run the application:
   ```bash
   ./run.sh
   ```
   
   Or directly with Java:
   ```bash
   java -jar target/aichat-github-1.0.jar
   ```

## Commands

- `/quit` - Exit the application
- `/clean` - Clear all dialog history
- `/model` - List available models
- `/model <name>` - Switch to a specific model
- `/help` - Show tinyAI project structure
- `/help <question>` - Ask a question about the tinyAI project (uses RAG)

## Architecture

The application follows SOLID principles with a multi-stage processing pipeline:

### Stage 0: Knowledge Retrieval
- Searches the tinyAI embeddings database for relevant context
- Checks GitHub MCP server availability
- Combines RAG and GitHub context for responses

### Stage 1: Task Decision
- Analyzes user request to identify the current task
- Determines if it's a new task or continuation
- Extracts requirements and constraints

### Stage 2: Answer Generation
- Generates response using LLM with full context
- Incorporates RAG knowledge and GitHub repository information
- Addresses the specific task requirements

### Stage 3: Task Completion Evaluation
- Evaluates whether the response satisfied the task
- Updates task status in database
- Provides verification summary

## Project Structure

```
aichat-github/
├── src/main/java/com/github/pvtitov/aichatgithub/
│   ├── AichatGithubApplication.java      # CLI entry point
│   ├── constants/
│   │   └── ApiConstants.java             # Configuration and prompts
│   ├── dto/
│   │   ├── EmbeddingSearchResult.java    # RAG search result
│   │   ├── LlmStructuredResponse.java    # Structured LLM response
│   │   ├── TaskCompletionStatus.java     # Task evaluation result
│   │   └── TaskDecisionResponse.java     # Task decision result
│   ├── model/
│   │   ├── ChatMessage.java              # Chat message entity
│   │   └── Task.java                     # Task entity
│   ├── repository/
│   │   ├── DialogHistoryRepository.java  # Dialog history DB operations
│   │   ├── EmbeddingRepository.java      # Embedding storage access
│   │   └── TaskRepository.java           # Task DB operations
│   └── service/
│       ├── ChatService.java              # Chat service interface
│       ├── ChatServiceImpl.java          # Chat orchestration
│       ├── EmbeddingSearchService.java   # RAG search
│       ├── GitHubMcpService.java         # GitHub MCP integration
│       ├── LlmService.java               # LLM service interface
│       ├── LlmServiceRegistry.java       # LLM model registry
│       ├── LlmModel.java                 # LLM model descriptor
│       ├── LlmResponse.java              # LLM response wrapper
│       └── OllamaLlmService.java         # Ollama API client
├── pom.xml
└── run.sh
```

## Configuration

### Constants (in `ApiConstants.java`)

- `OLLAMA_URL`: `http://localhost:11434`
- `OLLAMA_DEFAULT_MODEL`: `llama3.2:1b`
- `OLLAMA_MODEL`: `nomic-embed-text` (for embeddings)
- `EMBEDDING_DB_PATH`: `../tinyAI/embeddings.db`
- `GITHUB_MCP_SERVER_URL`: `http://localhost:3000`
- `TINYAI_REPO_URL`: `https://github.com/Headmast/tinyAI.git`
- `TINYAI_REPO_PATH`: `../tinyAI`

### Environment Variables

No environment variables required. All configuration is in `ApiConstants.java`.

## GitHub MCP Integration

The application integrates with a GitHub MCP server to:

1. **Read Files**: Access source code, documentation, and other files from the repository
2. **List Branches**: View available branches in the repository
3. **View Commits**: Access commit history and changes

If the MCP server is not available, the application falls back to reading files from the local repository path.

### MCP Server Setup

To enable full GitHub integration:

1. Install and configure a GitHub MCP server
2. Start it on `localhost:3000`
3. Ensure it has access to `https://github.com/Headmast/tinyAI.git`

## RAG Integration

On every user request:
1. The application generates an embedding using Ollama's `nomic-embed-text` model
2. Searches the tinyAI embedding database (`embeddings.db`) for similar content
3. Includes found context in the system prompt to the LLM
4. Displays sources at the end of the response

The embeddings database should be located at the path specified in `ApiConstants.EMBEDDING_DB_PATH`.

## Database

The application uses SQLite (`aichat-github.db`) with two tables:

1. **dialog_history**: Stores all conversation messages with token usage
2. **tasks**: Stores extracted task information with requirements and verification status

## License

This project is for testing and educational purposes.
