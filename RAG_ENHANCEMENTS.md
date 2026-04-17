# Enhanced RAG System with Sources, Citations, and "Don't Know" Mode

## Overview

The RAG (Retrieval-Augmented Generation) system has been enhanced to provide:

1. **Mandatory Sources** - Every answer includes source documents with section/chunk identifiers
2. **Mandatory Citations** - Direct quotes from retrieved knowledge chunks
3. **"Don't Know" Mode** - When relevance is below threshold, the assistant must say "I don't know" and ask for clarification

## Architecture

### New Components

#### 1. CitationSource DTO
**File**: `aichat/src/main/java/com/github/pvtitov/aichat/dto/CitationSource.java`

Structured data holder for citation information:
- `chunkId` - Unique identifier for the text chunk
- `source` - Original file path
- `title` - Document title (filename)
- `section` - Section/heading name
- `quote` - Extract from the chunk (first 200 chars)
- `relevanceScore` - Cosine similarity score

#### 2. Enhanced ChatResponse DTO
**File**: `aichat/src/main/java/com/github/pvtitov/aichat/dto/ChatResponse.java`

Added fields:
- `sources` - List of `CitationSource` objects
- `citations` - List of quote strings
- `lowRelevance` - Boolean flag for low-relevance context

New method:
- `formatSourcesAndCitations()` - Formats sources and citations as markdown appendix

#### 3. Enhanced EmbeddingSearchService
**File**: `aichat/src/main/java/com/github/pvtitov/aichat/service/EmbeddingSearchService.java`

New inner class:
- `SearchContext` - Holds formatted context, structured citations, and max relevance score

New method:
- `formatResultsAsContextWithCitations()` - Returns both formatted context AND structured citations

#### 4. Enhanced ConversationState
**File**: `aichat/src/main/java/com/github/pvtitov/aichat/dto/state/ConversationState.java`

Added fields to preserve RAG metadata across conversation stages:
- `ragCitations` - List of citations
- `maxRelevanceScore` - Maximum similarity score from search
- `lowRelevanceContext` - Flag indicating low relevance

### Modified Components

#### ChatServiceImpl
**File**: `aichat/src/main/java/com/github/pvtitov/aichat/service/ChatServiceImpl.java`

**Enhanced `handleAwaitingPrompt()`**:
1. Uses `formatResultsAsContextWithCitations()` instead of basic formatting
2. Stores RAG metadata in conversation state
3. Detects low relevance (threshold: 0.75) and sets flag
4. Injects different prompt instructions based on relevance level:
   - **Low relevance**: Instructs LLM to say "I don't know" and ask for clarification
   - **Good relevance**: Instructs LLM to include sources and citations in response
5. Attaches RAG metadata to ChatResponse

**Enhanced `handlePlanApproval()`**:
- Preserves citation instructions in action prompt
- Attaches RAG metadata to response

**Enhanced `handleActionApproval()`**:
- Appends formatted sources and citations to final response
- Preserves RAG metadata in final ChatResponse

## Response Format

### High-Relevance Response Example
```
[Answer to user's question based on retrieved knowledge]

---

**Sources:**
1. [learn_c_the_hard_way.pdf > Pointers, relevance: 0.842]
2. [learn_c_the_hard_way.pdf > Memory Management, relevance: 0.798]

**Citations:**
> "Pointners are variables that store memory addresses. They allow direct memory access and manipulation..."
> "Memory management in C requires careful attention to allocation and deallocation to avoid leaks..."
```

### Low-Relevance Response Example
```
I don't have sufficient information to provide a confident answer to your question. Could you please clarify or rephrase your question? 

[Optional: partial information with uncertainty disclaimer]

---

**Sources:**
1. [learn_c_the_hard_way.pdf > Functions, relevance: 0.621]

**Citations:**
> "Functions in C are defined with a return type and parameter list..."
```

## Configuration

### Relevance Threshold

The low-relevance threshold is set in `ChatServiceImpl.handleAwaitingPrompt()`:

```java
double lowRelevanceThreshold = 0.75;
boolean isLowRelevance = searchContext.getMaxRelevanceScore() < lowRelevanceThreshold;
```

You can adjust this value or make it configurable via `application.properties`:

```properties
embedding.lowRelevanceThreshold=0.75
```

### Existing RAG Configuration

From `application.properties`:
```properties
embedding.similarityThreshold=0.75        # Minimum similarity to include a chunk
embedding.topKBeforeRerank=20             # Chunks before filtering
embedding.topKAfterRerank=5               # Chunks after filtering
embedding.queryRewrite.enabled=false      # Query rewriting
```

## Testing

### Test Script

Run the comprehensive test:

```bash
./test_rag_citations.sh
```

This script:
1. Creates embeddings for `learn_c_the_hard_way.pdf`
2. Asks 10 questions (mix of relevant and irrelevant)
3. Validates each response for:
   - Presence of sources
   - Presence of citations
   - "Don't know" mode triggering
   - Meaning match with context
4. Prints detailed results and summary

### Expected Results

**C Programming Questions (1-5, 7-8, 10)**:
- ✓ Should have sources
- ✓ Should have citations
- ✓ Should have normal responses

**Irrelevant Questions (6, 9)**:
- ⚠ Should trigger "don't know" mode
- ✓ Should ask for clarification

### Manual Testing

Start the servers:
```bash
./run_servers.sh
```

Ask questions via curl:
```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "How to use pointers in C?"}'
```

## Validation Checklist

For each response, verify:

- [ ] **Sources present**: Response includes `---\n**Sources:**` section
- [ ] **Citations present**: Response includes `**Citations:**` section with quotes
- [ ] **Format correct**: Sources follow pattern `[Title > Section, relevance: X.XXX]`
- [ ] **Quotes relevant**: Citations are actual text from source documents
- [ ] **Meaning matches**: Answer content aligns with cited sources
- [ ] **Low relevance handled**: If similarity < 0.75, response starts with "I don't know"

## Implementation Details

### Flow Diagram

```
User Query
    |
    v
[Embedding Search]
    |
    v
[formatResultsAsContextWithCitations()]
    |
    +---> Returns SearchContext {
    |         formattedContext: String
    |         citations: List<CitationSource>
    |         maxRelevanceScore: double
    |       }
    |
    v
[Check maxRelevanceScore vs threshold]
    |
    +---> If < 0.75: Set lowRelevanceContext = true
    |
    +---> If >= 0.75: Set lowRelevanceContext = false
    |
    v
[Build Prompt with appropriate instructions]
    |
    +---> Low relevance: "Say you don't know and ask for clarification"
    |
    +---> Good relevance: "Include sources and citations in your response"
    |
    v
[LLM Generates Response]
    |
    v
[ChatResponse with sources, citations, lowRelevance flag]
    |
    v
[Append formatted sources and citations to response]
    |
    v
[Return to User]
```

### Key Code Snippets

#### Citation Extraction (EmbeddingSearchService.java)
```java
// Extract a quote (first 200 chars or full content if shorter)
String quote = result.getContent().length() > 200 
    ? result.getContent().substring(0, 200) + "..." 
    : result.getContent();

CitationSource citation = new CitationSource(
    result.getChunkId(),
    result.getSource(),
    result.getTitle(),
    result.getSection(),
    quote,
    result.getSimilarityScore()
);
```

#### Low Relevance Detection (ChatServiceImpl.java)
```java
double lowRelevanceThreshold = 0.75;
boolean isLowRelevance = searchContext.getMaxRelevanceScore() < lowRelevanceThreshold;
conversationState.setLowRelevanceContext(isLowRelevance);
```

#### Prompt Instruction for Good Relevance
```java
citationInstruction = "\n\nIMPORTANT: You MUST structure your response as follows:\n" +
    "1. Provide a clear, direct answer to the user's request\n" +
    "2. At the end of your response, add a '---' separator\n" +
    "3. List all sources you used with their section names and chunk IDs\n" +
    "4. Include relevant quotes (citations) from the retrieved knowledge to support your answer\n" +
    "Format:\n" +
    "---\n" +
    "**Sources:**\n" +
    "1. [Source title > Section name, relevance score]\n" +
    "\n**Citations:**\n" +
    "> \"exact quote from source\"\n" +
    "> \"another quote from source\"";
```

## Benefits

1. **Transparency**: Users can see exactly which sources informed the answer
2. **Verifiability**: Citations allow users to verify claims against source material
3. **Trust**: "Don't know" mode prevents hallucination on unfamiliar topics
4. **Accountability**: Source tracking enables quality assessment of RAG pipeline
5. **Debugging**: Structured citation data enables automated testing and validation

## Future Enhancements

1. **Configurable threshold**: Make low-relevance threshold a Spring property
2. **Citation extraction**: Use LLM to extract more meaningful quotes instead of first 200 chars
3. **Source ranking**: Sort sources by relevance in final response
4. **Interactive citations**: Make citations clickable in web UI to jump to source
5. **Citation validation**: Use LLM to verify that citations actually support the answer
6. **Multi-document citations**: Track which answer parts came from which sources
