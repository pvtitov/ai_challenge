# AI Chat Lite

A chat application with GigaChat LLM and RAG (Retrieval-Augmented Generation) support, available both as a **CLI** and as a **Web Server** with a terminal-like interface.

## Features

- **CLI & Web Modes**: Run as a terminal app or as an HTTP server with browser-based terminal UI
- **GigaChat Integration**: Uses Sberbank's GigaChat API for AI responses
- **RAG Support**: Automatically searches indexed embeddings for context on every request
- **Full Dialog History**: Saves complete conversation history in SQLite database without limitations
- **Task Tracking**: Analyzes and tracks tasks from conversations with requirements and invariants
- **Token Usage**: Displays token consumption (input, output, total) for each response
- **Source Citations**: Shows information sources found via RAG

## Commands

- `/quit` - Exit the application (CLI) / disconnect (web)
- `/clean` - Clear all dialog history
- `/model` - List available LLM models
- `/model <name>` - Switch to a specific model

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- GigaChat API credentials (Base64-encoded client ID:secret)
- Ollama running locally (for RAG embedding search) - optional but recommended

## Building

```bash
mvn clean package
```

## Running

### CLI Mode (default)

1. Set your GigaChat API credentials:
   ```bash
   export GIGACHAT_API_CREDENTIALS='your_base64_encoded_credentials'
   ```

2. Run the application:
   ```bash
   ./run.sh
   ```

   Or directly with Java:
   ```bash
   java -jar target/aichat-c-learning-1.0.jar
   ```

### Web Server Mode

Run as an HTTP server with a browser-based terminal-like UI:

```bash
./run.sh server
```

Or with a custom port:

```bash
./run.sh server 9090
```

Or directly with Java:

```bash
export GIGACHAT_API_CREDENTIALS='your_base64_encoded_credentials'
java -cp target/aichat-c-learning-1.0.jar com.github.pvtitov.aichatclearning.ServerMain [port]
```

The server binds to **all network interfaces** (`0.0.0.0`), making it accessible from your local network.

After starting, you'll see output like:

```
============================================================
  AI Chat Lite - Web Server
============================================================
  Local access:  http://localhost:8080
  Network access: http://192.168.1.100:8080
  Open in browser: http://localhost:8080
============================================================
```

### Accessing from Another Device on Your Local Network

1. **Start the server** on your machine:
   ```bash
   ./run.sh server
   ```

2. **Note the "Network access" URL** printed on startup (e.g., `http://192.168.1.100:8080`).

3. **Open a browser** on any device on the same network and navigate to that URL.

4. The terminal-like UI will connect automatically via WebSocket.

> **Note:** Make sure your firewall allows incoming connections on the chosen port (default `8080`).
> On macOS, you may need to allow Java through the firewall in System Settings > Network > Firewall.

## Architecture

The application follows SOLID principles:

- **Single Responsibility**: Each class has a single, well-defined purpose
  - `GigaChatApiService`: Handles GigaChat API communication
  - `EmbeddingSearchService`: Manages RAG search functionality
  - `DialogHistoryRepository`: Database operations for dialog history
  - `TaskRepository`: Database operations for task tracking
  - `ChatServiceImpl`: Orchestrates the chat workflow
  - `ChatServer`: Embedded Jetty HTTP server
  - `TerminalWebSocket`: WebSocket handler for web terminal sessions

- **Open/Closed**: Services can be extended without modification
- **Liskov Substitution**: Repository interfaces allow for alternative implementations
- **Interface Segregation**: Clean, focused interfaces
- **Dependency Inversion**: High-level modules depend on abstractions

## Project Structure

```
aichat-c-learning/
├── src/main/java/com/github/pvtitov/aichatclearning/
│   ├── AichatCLearningApplication.java      # CLI entry point
│   ├── ServerMain.java                       # HTTP server entry point
│   ├── server/
│   │   ├── ChatServer.java                   # Embedded Jetty server setup
│   │   ├── TerminalWebSocket.java            # WebSocket connection handler
│   │   └── TerminalSession.java              # Per-client terminal session
│   ├── constants/
│   │   ├── ApiConstants.java                 # API URLs and configuration
│   │   └── DatabaseConstants.java            # Database schema
│   ├── dto/
│   │   ├── LlmStructuredResponse.java        # Structured LLM response format
│   │   └── EmbeddingSearchResult.java        # RAG search result
│   ├── model/
│   │   ├── ChatMessage.java                  # Chat message entity
│   │   └── Task.java                         # Task entity with verification
│   ├── repository/
│   │   ├── DialogHistoryRepository.java      # Dialog history DB operations
│   │   ├── TaskRepository.java               # Task DB operations
│   │   └── EmbeddingRepository.java          # Embedding storage access
│   └── service/
│       ├── ChatService.java                  # Chat service interface
│       ├── ChatServiceImpl.java              # Chat orchestration
│       ├── GigaChatApiService.java           # GigaChat API client
│       ├── LlmService.java                   # LLM provider interface
│       ├── LlmServiceRegistry.java           # Multi-LLM provider management
│       └── EmbeddingSearchService.java       # RAG search
├── src/main/resources/static/
│   └── index.html                            # Web terminal UI
├── pom.xml
└── run.sh
```

## Database

The application uses SQLite (`aichat-c-learning.db`) with two tables:

1. **dialog_history**: Stores all conversation messages with token usage
2. **tasks**: Stores extracted task information with requirements and verification status

## RAG Integration

On every user request:
1. The application generates an embedding using Ollama's `nomic-embed-text` model
2. Searches the embedding database (`embeddings.db`) for similar content
3. Includes found context in the system prompt to GigaChat
4. Displays sources at the end of the response

## Web Terminal UI

The web client provides the same experience as the CLI in a browser:

- **Terminal-style appearance**: Dark theme with monospace font, mimicking a real terminal
- **WebSocket-based**: Real-time communication for instant response streaming
- **Command history**: Use `Up`/`Down` arrow keys to navigate previous commands
- **Auto-reconnect**: Automatically attempts to reconnect if the connection drops
- **Status bar**: Shows connection status at the bottom
- **Responsive**: Works on desktop and mobile browsers

## Configuration

Environment variables:
- `GIGACHAT_API_CREDENTIALS` - Base64-encoded GigaChat API credentials (required)

Constants (in `ApiConstants.java`):
- GigaChat API URLs
- Ollama URL and model
- Embedding database path

Server defaults:
- Port: `8080` (override via command-line argument)
- Bind address: `0.0.0.0` (all interfaces)

## License

This project is for testing and educational purposes.
