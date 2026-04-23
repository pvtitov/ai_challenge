# AI Chat Lite

A simple CLI chat application with GigaChat LLM and RAG (Retrieval-Augmented Generation) support.

## Features

- **Simple CLI Interface**: Clean command-line chat between user and AI
- **GigaChat Integration**: Uses Sberbank's GigaChat API for AI responses
- **RAG Support**: Automatically searches indexed embeddings for context on every request
- **Full Dialog History**: Saves complete conversation history in SQLite database without limitations
- **Task Tracking**: Analyzes and tracks tasks from conversations with requirements and invariants
- **Token Usage**: Displays token consumption (input, output, total) for each response
- **Source Citations**: Shows information sources found via RAG

## Commands

- `/quit` - Exit the application
- `/clean` - Clear all dialog history

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

## Architecture

The application follows SOLID principles:

- **Single Responsibility**: Each class has a single, well-defined purpose
  - `GigaChatApiService`: Handles GigaChat API communication
  - `EmbeddingSearchService`: Manages RAG search functionality
  - `DialogHistoryRepository`: Database operations for dialog history
  - `TaskRepository`: Database operations for task tracking
  - `ChatServiceImpl`: Orchestrates the chat workflow

- **Open/Closed**: Services can be extended without modification
- **Liskov Substitution**: Repository interfaces allow for alternative implementations
- **Interface Segregation**: Clean, focused interfaces
- **Dependency Inversion**: High-level modules depend on abstractions

## Project Structure

```
aichat-c-learning/
├── src/main/java/com/github/pvtitov/aichatlite/
│   ├── AichatLiteApplication.java      # CLI entry point
│   ├── constants/
│   │   ├── ApiConstants.java           # API URLs and configuration
│   │   └── DatabaseConstants.java      # Database schema
│   ├── dto/
│   │   ├── LlmStructuredResponse.java  # Structured LLM response format
│   │   └── EmbeddingSearchResult.java  # RAG search result
│   ├── model/
│   │   ├── ChatMessage.java            # Chat message entity
│   │   └── Task.java                   # Task entity with verification
│   ├── repository/
│   │   ├── DialogHistoryRepository.java # Dialog history DB operations
│   │   ├── TaskRepository.java          # Task DB operations
│   │   └── EmbeddingRepository.java     # Embedding storage access
│   └── service/
│       ├── ChatService.java             # Chat service interface
│       ├── ChatServiceImpl.java         # Chat orchestration
│       ├── GigaChatApiService.java      # GigaChat API client
│       └── EmbeddingSearchService.java  # RAG search
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

## Structured Response Format

The application requests structured JSON responses from the LLM:

```json
{
  "response": "AI's response to the user",
  "tasks": [
    {
      "title": "Task title",
      "requirements": ["req1", "req2"],
      "invariants": ["inv1", "inv2"],
      "verification": {
        "verified": true,
        "summary": "Verification conclusion"
      }
    }
  ]
}
```

## Configuration

Environment variables:
- `GIGACHAT_API_CREDENTIALS` - Base64-encoded GigaChat API credentials (required)

Constants (in `ApiConstants.java`):
- GigaChat API URLs
- Ollama URL and model
- Embedding database path

## License

This project is for testing and educational purposes.
