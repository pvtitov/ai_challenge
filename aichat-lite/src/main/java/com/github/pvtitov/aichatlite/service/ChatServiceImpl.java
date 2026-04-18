package com.github.pvtitov.aichatlite.service;

import com.github.pvtitov.aichatlite.constants.ApiConstants;
import com.github.pvtitov.aichatlite.dto.EmbeddingSearchResult;
import com.github.pvtitov.aichatlite.dto.LlmStructuredResponse;
import com.github.pvtitov.aichatlite.model.ChatMessage;
import com.github.pvtitov.aichatlite.model.Task;
import com.github.pvtitov.aichatlite.repository.DialogHistoryRepository;
import com.github.pvtitov.aichatlite.repository.TaskRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ChatServiceImpl implements ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);
    
    private final GigaChatApiService gigaChatApiService;
    private final EmbeddingSearchService embeddingSearchService;
    private final DialogHistoryRepository dialogHistoryRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    
    public ChatServiceImpl() {
        this.gigaChatApiService = new GigaChatApiService();
        this.embeddingSearchService = new EmbeddingSearchService();
        this.dialogHistoryRepository = new DialogHistoryRepository();
        this.taskRepository = new TaskRepository(dialogHistoryRepository.getConnection());
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    @Override
    public LlmStructuredResponse processMessage(String userMessage) {
        // 1. Save user message to history
        com.github.pvtitov.aichatlite.model.ChatMessage userMsg =
            new com.github.pvtitov.aichatlite.model.ChatMessage("user", userMessage);
        dialogHistoryRepository.save(userMsg);

        // 2. Search RAG for context
        String ragContext = "";
        List<EmbeddingSearchResult> searchResults = List.of();

        if (embeddingSearchService.isReady()) {
            System.out.println("[Searching knowledge base...]");
            searchResults = embeddingSearchService.search(userMessage);
            System.out.println("[Found " + searchResults.size() + " relevant source(s)]");
            if (!searchResults.isEmpty()) {
                ragContext = embeddingSearchService.formatResultsAsContext(searchResults);
            }
        } else {
            System.out.println("[Knowledge base not available - skipping RAG search]");
        }
        
        // 3. Build system prompt with RAG context and task information
        String systemPrompt = buildSystemPrompt(ragContext);
        
        // 4. Get conversation history
        List<GigaChatApiService.ChatMessage> history = getHistoryForApi();
        
        // 5. Call GigaChat API
        GigaChatApiService.GigaChatResponse apiResponse = 
            gigaChatApiService.callChatApi(history, systemPrompt);
        
        // 6. Parse structured response
        LlmStructuredResponse structuredResponse = parseStructuredResponse(apiResponse.getContent());
        
        // 7. Set token usage
        LlmStructuredResponse.TokenUsage tokenUsage = new LlmStructuredResponse.TokenUsage();
        tokenUsage.setInput(apiResponse.getPromptTokens());
        tokenUsage.setOutput(apiResponse.getCompletionTokens());
        tokenUsage.setTotal(apiResponse.getTotalTokens());
        structuredResponse.setTokens(tokenUsage);
        
        // 8. Attach RAG sources for display
        structuredResponse.setRagSources(searchResults);
        
        // 8. Save assistant response to history
        com.github.pvtitov.aichatlite.model.ChatMessage assistantMsg = 
            new com.github.pvtitov.aichatlite.model.ChatMessage("assistant", apiResponse.getContent());
        assistantMsg.setPromptTokens(apiResponse.getPromptTokens());
        assistantMsg.setCompletionTokens(apiResponse.getCompletionTokens());
        assistantMsg.setTotalTokens(apiResponse.getTotalTokens());
        dialogHistoryRepository.save(assistantMsg);
        
        // 9. Save tasks if any
        if (structuredResponse.getTasks() != null) {
            for (Task task : structuredResponse.getTasks()) {
                task.setDialogMessageId(assistantMsg.getId());
                taskRepository.save(task);
            }
        }
        
        // 10. Store search results for display
        structuredResponse.setTasks(structuredResponse.getTasks());
        
        return structuredResponse;
    }
    
    private String buildSystemPrompt(String ragContext) {
        StringBuilder prompt = new StringBuilder(ApiConstants.SYSTEM_PROMPT_TEMPLATE);
        
        if (!ragContext.isEmpty()) {
            prompt.append("\n\n").append(ragContext);
        }
        
        // Add instruction to include sources
        prompt.append("\n\nIMPORTANT: If you use information from the knowledge base, " +
                     "list the sources at the end of your response in this format:\n" +
                     "Information sources:\n" +
                     "1. [Source details]\n" +
                     "2. [Source details]");
        
        return prompt.toString();
    }
    
    private List<GigaChatApiService.ChatMessage> getHistoryForApi() {
        List<com.github.pvtitov.aichatlite.model.ChatMessage> history = dialogHistoryRepository.findAll();
        List<GigaChatApiService.ChatMessage> apiMessages = new ArrayList<>();
        
        for (com.github.pvtitov.aichatlite.model.ChatMessage msg : history) {
            apiMessages.add(new GigaChatApiService.ChatMessage(msg.getRole(), msg.getContent()));
        }
        
        return apiMessages;
    }
    
    private LlmStructuredResponse parseStructuredResponse(String content) {
        LlmStructuredResponse response = new LlmStructuredResponse();

        try {
            // Try to extract JSON from the response (in case there's markdown or other text)
            String jsonContent = extractJson(content);

            LlmStructuredResponse parsed = objectMapper.readValue(jsonContent, LlmStructuredResponse.class);
            response.setResponse(parsed.getResponse());
            response.setTasks(parsed.getTasks());

            if (parsed.getTokens() != null) {
                response.setTokens(parsed.getTokens());
            }
        } catch (Exception e) {
            logger.warn("Failed to parse structured response: {}", e.getMessage());
            // Fallback: extract response field manually from the JSON-like text
            String extractedResponse = extractResponseField(content);
            response.setResponse(extractedResponse != null ? extractedResponse : content);
            response.setTasks(new ArrayList<>());
            response.setJsonParseFailed(true);
        }

        return response;
    }

    /**
     * Extract the "response" field value from a JSON-like string using string operations.
     * Handles cases where LLM returns malformed JSON.
     */
    private String extractResponseField(String content) {
        // Find "response": " pattern
        String marker = "\"response\":";
        int startIdx = content.indexOf(marker);
        if (startIdx == -1) {
            return null;
        }

        // Find the opening quote after the colon
        int quoteStart = content.indexOf("\"", startIdx + marker.length());
        if (quoteStart == -1) {
            return null;
        }

        // Find the closing quote - need to handle escaped quotes
        StringBuilder value = new StringBuilder();
        int i = quoteStart + 1;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (c == '\\' && i + 1 < content.length()) {
                char next = content.charAt(i + 1);
                switch (next) {
                    case '"': value.append('"'); i += 2; continue;
                    case 'n': value.append('\n'); i += 2; continue;
                    case 'r': value.append('\r'); i += 2; continue;
                    case 't': value.append('\t'); i += 2; continue;
                    case '\\': value.append('\\'); i += 2; continue;
                    default: value.append(c); i++; continue;
                }
            }
            if (c == '"') {
                // Found closing quote
                return value.toString();
            }
            value.append(c);
            i++;
        }

        return value.length() > 0 ? value.toString() : null;
    }
    
    private String extractJson(String content) {
        // Remove markdown code blocks if present
        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        
        // Try to find JSON object boundaries
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        
        return content;
    }
    
    @Override
    public void clearHistory() {
        taskRepository.deleteAll();
        dialogHistoryRepository.deleteAll();
        logger.info("Dialog history cleared");
    }
    
    @Override
    public void shutdown() {
        dialogHistoryRepository.close();
        embeddingSearchService.close();
    }
}
