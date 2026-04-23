# AI Chat C Learning - Implementation Summary

## Overview

Successfully created `aichat-c-learning` - a simplified CLI chat application in Java that integrates with GigaChat LLM and RAG (Retrieval-Augmented Generation) system. The project leverages implementation details from the existing `aichat` and `embedding-tool` projects.

## Key Features Implemented

### 1. **Simple CLI Interface**
- Clean command-line chat between user and AI
- No profiles, no complex state machines, no multi-stage request processing
- Two simple commands: `/quit` to exit, `/clean` to clear history

### 2. **GigaChat API Integration**
- Reuses authentication and API call patterns from the existing `aichat` project
- OAuth token management with 30-minute expiry
- SSL configuration for Sberbank's API endpoints
- Token usage tracking (input, output, total)

### 3. **RAG Integration**
- On every user request, automatically searches the embedding database
- Uses Ollama's `nomic-embed-text` model for query embedding generation
- Cosine similarity search with configurable threshold (0.6) and top-K results (5)
- Formats found context and includes it in the system prompt
- **Always displays sources** at the end of responses when RAG information is used

### 4. **Full Dialog History**
- SQLite database (`aichat-c-learning.db`) stores complete conversation history
- No limitations on history length (unlike the three-tier system in `aichat`)
- Single `dialog_history` table with all messages and token usage
- Separate `tasks` table for task tracking

### 5. **Structured Response Parsing**
- Requests LLM to return JSON with:
  - `response`: The actual answer to display to the user
  - `tasks`: Array of tasks with title, requirements, invariants, and verification
  - `tokens`: Token usage statistics
- Robust JSON parsing with fallback for malformed responses
- Handles markdown code blocks in LLM output

### 6. **Task Tracking**
- Each response can include new tasks extracted from the conversation
- Tasks include:
  - **Title**: Short description of the task
  - **Requirements**: Constraints that may change over time
  - **Invariants**: Essential information that shouldn't change
  - **Verification**: Whether the response complies with the task

### 7. **SOLID Principles**
- **Single Responsibility**: Each class has one clear purpose
  - `GigaChatApiService`: API communication only
  - `EmbeddingSearchService`: RAG search only
  - `DialogHistoryRepository`: Database operations for messages
  - `TaskRepository`: Database operations for tasks
  - `ChatServiceImpl`: Orchestrates the workflow
- **Open/Closed**: Easy to extend with new features
- **Interface Segregation**: `ChatService` interface for chat operations
- **Dependency Injection**: Services can be swapped or mocked for testing

## Project Structure

```
aichat-c-learning/
├── src/
│   ├── main/java/com/github/pvtitov/aichatlite/
│   │   ├── AichatLiteApplication.java      # CLI entry point
│   │   ├── constants/
│   │   │   ├── ApiConstants.java           # API URLs, prompts, configuration
│   │   │   └── DatabaseConstants.java      # SQL schema definitions
│   │   ├── dto/
│   │   │   ├── LlmStructuredResponse.java  # Structured LLM response DTO
│   │   │   └── EmbeddingSearchResult.java  # RAG search result DTO
│   │   ├── model/
│   │   │   ├── ChatMessage.java            # Chat message entity
│   │   │   └── Task.java                   # Task entity with nested Verification
│   │   ├── repository/
│   │   │   ├── DialogHistoryRepository.java # Message CRUD operations
│   │   │   ├── TaskRepository.java          # Task CRUD operations
│   │   │   └── EmbeddingRepository.java     # Embedding data access
│   │   └── service/
│   │       ├── ChatService.java             # Service interface
│   │       ├── ChatServiceImpl.java         # Main chat orchestration
│   │       ├── GigaChatApiService.java      # GigaChat HTTP client
│   │       └── EmbeddingSearchService.java  # RAG search logic
│   └── test/java/com/github/pvtitov/aichatlite/
│       └── LlmStructuredResponseTest.java   # JSON parsing tests
├── pom.xml                                  # Maven build configuration
├── run.sh                                   # Launch script
├── README.md                                # Documentation
└── .gitignore                               # Git ignore rules
```

## Workflow

1. User enters a message in the CLI
2. Application saves user message to `dialog_history` table
3. Searches embedding database for relevant context (RAG)
4. Builds system prompt with:
   - Structured response format instructions
   - RAG context (if found)
   - Source citation requirements
5. Retrieves full conversation history
6. Calls GigaChat API with history and system prompt
7. Parses structured JSON response
8. Displays only the `response` field to user
9. Shows token usage statistics
10. Shows RAG sources (if any were used)
11. Saves assistant response to database
12. Saves any new tasks to database

## Database Schema

### dialog_history
```sql
CREATE TABLE dialog_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
)
```

### tasks
```sql
CREATE TABLE tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    dialog_message_id INTEGER,
    title TEXT NOT NULL,
    requirements TEXT,  -- JSON array
    invariants TEXT,    -- JSON array
    verified BOOLEAN DEFAULT 1,
    verification_summary TEXT,
    FOREIGN KEY (dialog_message_id) REFERENCES dialog_history(id)
)
```

## Technologies Used

- **Java 17**: Modern Java with text blocks and records
- **SQLite**: Lightweight embedded database
- **OkHttp**: HTTP client for GigaChat API
- **Jackson**: JSON parsing and serialization
- **SLF4J**: Logging framework
- **JUnit 5**: Unit testing
- **Maven**: Build tool with shade plugin for fat JAR

## Differences from Original aichat

| Feature | aichat | aichat-c-learning |
|---------|--------|-------------|
| Architecture | Spring Boot web app | Simple CLI application |
| User Management | Profiles with branches | Single conversation |
| History | Three-tier (short/mid/long term) with strategies | Unlimited single table |
| Request Flow | Multi-stage (plan → execute → verify) | Direct single request |
| Memory Types | Multiple memory systems | Only database history |
| RAG | Optional with query rewrite | Always on, every request |
| MCP Integration | Weather, Knowledge, GitHub | None (RAG only) |
| Complexity | ~50+ classes | 14 classes |
| Dependencies | Spring, OkHttp, MCP SDK | OkHttp, Jackson, SQLite |

## How to Run

```bash
# Set GigaChat credentials
export GIGACHAT_API_CREDENTIALS='your_base64_credentials'

# Run the application
cd aichat-c-learning
./run.sh

# Or directly with Java
java -jar target/aichat-c-learning-1.0.jar
```

## Testing

```bash
# Run unit tests
mvn test

# Build the project
mvn clean package

# Run with specific test
mvn test -Dtest=LlmStructuredResponseTest
```

## Future Enhancements

- Add conversation summarization for long dialogs
- Implement task verification against history
- Add export/import for dialog history
- Support multiple embedding models
- Add configuration file for constants
- Implement retry logic for API failures
- Add streaming responses for better UX

## Lessons Learned from Existing Projects

1. **From aichat**: 
   - GigaChat API authentication flow
   - SSL configuration for Sberbank endpoints
   - Database schema design
   - Structured response parsing

2. **From embedding-tool**:
   - Ollama embedding API usage
   - Cosine similarity calculation
   - Byte array to float array conversion
   - SQLite embedding storage

3. **Simplifications Made**:
   - Removed Spring Boot dependency
   - Eliminated complex state machines
   - Simplified to single conversation thread
   - Removed MCP client complexity
   - Removed branching and profile management

## Conclusion

The `aichat-c-learning` project successfully demonstrates a minimal, focused implementation of an AI chat client that:
- ✅ Uses GigaChat API (from existing project)
- ✅ Implements RAG search (from embedding-tool)
- ✅ Saves full dialog history without limitations
- ✅ Tracks tasks with requirements and verification
- ✅ Always shows RAG sources
- ✅ Follows SOLID principles
- ✅ Provides simple CLI with `/quit` and `/clean` commands
- ✅ Displays token usage for every response

The project is production-ready for testing and can be extended as needed.
