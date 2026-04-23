package com.github.pvtitov.aichatclearning.service;

import com.github.pvtitov.aichatclearning.constants.ApiConstants;
import com.github.pvtitov.aichatclearning.dto.EmbeddingSearchResult;
import com.github.pvtitov.aichatclearning.dto.LlmStructuredResponse;
import com.github.pvtitov.aichatclearning.dto.TaskCompletionStatus;
import com.github.pvtitov.aichatclearning.dto.TaskDecisionResponse;
import com.github.pvtitov.aichatclearning.model.ChatMessage;
import com.github.pvtitov.aichatclearning.model.Task;
import com.github.pvtitov.aichatclearning.repository.DialogHistoryRepository;
import com.github.pvtitov.aichatclearning.repository.TaskRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatServiceImpl implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final LlmServiceRegistry llmServiceRegistry;
    private final EmbeddingSearchService embeddingSearchService;
    private final DialogHistoryRepository dialogHistoryRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    public ChatServiceImpl() {
        this.llmServiceRegistry = new LlmServiceRegistry();
        this.embeddingSearchService = new EmbeddingSearchService();
        this.dialogHistoryRepository = new DialogHistoryRepository();
        this.taskRepository = new TaskRepository(dialogHistoryRepository.getConnection());
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public LlmServiceRegistry getLlmServiceRegistry() {
        return llmServiceRegistry;
    }

    @Override
    public LlmStructuredResponse processMessage(String userMessage) {
        LlmService llmService = llmServiceRegistry.getCurrentService();
        
        // Token usage tracker for all 3 requests
        TokenUsageTracker totalTokens = new TokenUsageTracker();

        // Save user message to history (will save assistant response later)
        com.github.pvtitov.aichatclearning.model.ChatMessage userMsg =
            new com.github.pvtitov.aichatclearning.model.ChatMessage("user", userMessage);
        dialogHistoryRepository.save(userMsg);

        // Search RAG for context
        String ragContext = "";
        List<EmbeddingSearchResult> searchResults = List.of();

        if (embeddingSearchService.isReady()) {
            System.out.println("[Stage 0: Searching knowledge base...]");
            searchResults = embeddingSearchService.search(userMessage);
            System.out.println("[Found " + searchResults.size() + " relevant source(s)]");
            if (!searchResults.isEmpty()) {
                ragContext = embeddingSearchService.formatResultsAsContext(searchResults);
            }
        } else {
            System.out.println("[Knowledge base not available - skipping RAG search]");
        }

        // === STAGE 1: Task Decision ===
        System.out.println("\n[Stage 1: Deciding task context...]");
        TaskDecisionWithTokens taskDecisionResult = performTaskDecision(userMessage, ragContext, llmService);
        totalTokens.add(taskDecisionResult.tokens);

        TaskDecisionResponse taskDecision = taskDecisionResult.response;

        // Build or update task based on decision
        Task currentTask = buildCurrentTask(taskDecision);

        // Print task decision result
        printTaskDecision(currentTask, taskDecision.isNewTask());

        // === STAGE 2: Get Answer with Task Context ===
        System.out.println("\n[Stage 2: Getting answer from LLM...]");
        String answerSystemPrompt = buildAnswerSystemPromptWithTask(currentTask, ragContext);
        List<LlmService.LlmMessage> history = getHistoryForApi();

        LlmResponse apiResponse = llmService.callChatApi(history, answerSystemPrompt);

        // Track stage 2 tokens
        totalTokens.add(apiResponse.getPromptTokens(), apiResponse.getCompletionTokens(), apiResponse.getTotalTokens());

        // Save assistant response to history
        com.github.pvtitov.aichatclearning.model.ChatMessage assistantMsg =
            new com.github.pvtitov.aichatclearning.model.ChatMessage("assistant", apiResponse.getContent());
        assistantMsg.setPromptTokens(apiResponse.getPromptTokens());
        assistantMsg.setCompletionTokens(apiResponse.getCompletionTokens());
        assistantMsg.setTotalTokens(apiResponse.getTotalTokens());
        dialogHistoryRepository.save(assistantMsg);

        // Build structured response
        LlmStructuredResponse structuredResponse = new LlmStructuredResponse();
        structuredResponse.setResponse(apiResponse.getContent());
        LlmStructuredResponse.TokenUsage answerTokens = new LlmStructuredResponse.TokenUsage();
        answerTokens.setInput(apiResponse.getPromptTokens());
        answerTokens.setOutput(apiResponse.getCompletionTokens());
        answerTokens.setTotal(apiResponse.getTotalTokens());
        structuredResponse.setTokens(answerTokens);
        structuredResponse.setRagSources(searchResults);
        structuredResponse.setJsonParseFailed(false);

        // Print AI response immediately after stage 2
        System.out.println("\nAI: " + apiResponse.getContent());

        // === STAGE 3: Task Completion Status ===
        System.out.println("\n[Stage 3: Evaluating task completion...]");
        TaskCompletionWithTokens completionResult = performTaskCompletionEvaluation(
            userMessage,
            apiResponse.getContent(),
            currentTask,
            ragContext,
            llmService
        );
        totalTokens.add(completionResult.tokens);

        TaskCompletionStatus completionStatus = completionResult.status;

        // Update task with completion status and save
        currentTask.setStatus(completionStatus);
        taskRepository.save(currentTask);

        printTaskCompletionStatus(completionStatus);

        // Print RAG sources if available
        if (searchResults != null && !searchResults.isEmpty()) {
            List<String> uniqueSources = searchResults.stream()
                    .map(EmbeddingSearchResult::getSource)
                    .filter(s -> s != null && !s.isEmpty())
                    .distinct()
                    .toList();
            if (!uniqueSources.isEmpty()) {
                System.out.println("\nInformation sources:");
                for (String source : uniqueSources) {
                    System.out.println("- " + source);
                }
            }
        }

        // Print total token usage summary after all stages
        printTokenSummary(totalTokens);

        // Set total token usage in response (sum of all 3 requests)
        LlmStructuredResponse.TokenUsage totalTokenUsage = new LlmStructuredResponse.TokenUsage();
        totalTokenUsage.setInput(totalTokens.input);
        totalTokenUsage.setOutput(totalTokens.output);
        totalTokenUsage.setTotal(totalTokens.total);
        structuredResponse.setTokens(totalTokenUsage);

        return structuredResponse;
    }

    /**
     * Stage 1: Decide what the current task is and if requirements changed.
     */
    private TaskDecisionWithTokens performTaskDecision(String userMessage, String ragContext, LlmService llmService) {
        try {
            // Build context with previous task info
            StringBuilder contextBuilder = new StringBuilder();
            Task latestTask = taskRepository.findLatest();

            if (latestTask != null) {
                contextBuilder.append("\n\nPREVIOUS TASK:\n");
                contextBuilder.append("ID: ").append(latestTask.getId()).append("\n");
                contextBuilder.append("Title: ").append(latestTask.getTitle()).append("\n");
                if (latestTask.getRequirements() != null && !latestTask.getRequirements().isEmpty()) {
                    contextBuilder.append("Requirements:\n");
                    for (String req : latestTask.getRequirements()) {
                        contextBuilder.append("- ").append(req).append("\n");
                    }
                }
                if (latestTask.getStatus() != null) {
                    contextBuilder.append("Was completed: ").append(latestTask.getStatus().isCompleted()).append("\n");
                }
            }

            if (!ragContext.isEmpty()) {
                contextBuilder.append("\n\nKNOWLEDGE BASE CONTEXT:\n").append(ragContext);
            }

            // Create message with user request
            List<LlmService.LlmMessage> messages = new ArrayList<>();
            messages.add(new LlmService.LlmMessage("user", userMessage));

            LlmResponse response = llmService.callTaskDecisionApi(
                ApiConstants.TASK_DECISION_SYSTEM_PROMPT + contextBuilder.toString(),
                messages
            );

            TaskDecisionResponse decision = parseTaskDecisionResponse(response.getContent());
            
            // If parsing failed and returned null, use fallback
            if (decision == null) {
                logger.warn("Task decision parsing returned null, using fallback");
                decision = new TaskDecisionResponse();
                decision.setTaskTitle("User request");
                decision.setNewTask(true);
                decision.setRequirements(List.of());
                decision.setAddedRequirements(List.of());
            }
            
            TokenUsage tokens = new TokenUsage(response.getPromptTokens(), response.getCompletionTokens(), response.getTotalTokens());

            return new TaskDecisionWithTokens(decision, tokens);

        } catch (Exception e) {
            logger.warn("Task decision failed: {}", e.getMessage());
            // Return a default decision
            TaskDecisionResponse fallback = new TaskDecisionResponse();
            fallback.setTaskTitle("User request");
            fallback.setNewTask(true);
            fallback.setRequirements(List.of());
            fallback.setAddedRequirements(List.of());
            return new TaskDecisionWithTokens(fallback, new TokenUsage(0, 0, 0));
        }
    }

    /**
     * Build the current task object based on task decision.
     * If it's an existing task, merge requirements.
     * If it's a new task, create from scratch.
     */
    private Task buildCurrentTask(TaskDecisionResponse decision) {
        Task task = new Task();

        if (decision == null) {
            task.setTitle("User request");
            task.setRequirements(new ArrayList<>());
            return task;
        }

        task.setTitle(decision.getTaskTitle());

        if (decision.isNewTask()) {
            // New task - use requirements as-is
            task.setRequirements(decision.getRequirements() != null ? decision.getRequirements() : new ArrayList<>());
        } else {
            // Existing task - merge with previous task requirements
            Long existingId = decision.getExistingTaskId();
            if (existingId != null) {
                Task existingTask = taskRepository.findById(existingId);
                if (existingTask != null) {
                    // Combine old requirements with new ones (avoid duplicates)
                    Set<String> allRequirements = new HashSet<>();
                    if (existingTask.getRequirements() != null) {
                        allRequirements.addAll(existingTask.getRequirements());
                    }
                    if (decision.getRequirements() != null) {
                        allRequirements.addAll(decision.getRequirements());
                    }
                    task.setRequirements(new ArrayList<>(allRequirements));
                } else {
                    task.setRequirements(decision.getRequirements() != null ? decision.getRequirements() : new ArrayList<>());
                }
            } else {
                // existingTaskId is null - treat as new task with given requirements
                task.setRequirements(decision.getRequirements() != null ? decision.getRequirements() : new ArrayList<>());
            }
        }

        return task;
    }

    /**
     * Stage 2: Build system prompt with emphasized task context.
     */
    private String buildAnswerSystemPromptWithTask(Task currentTask, String ragContext) {
        StringBuilder prompt = new StringBuilder(ApiConstants.ANSWER_SYSTEM_PROMPT);

        // Add task context with HIGH emphasis
        prompt.append("\n\n========== CURRENT TASK (HIGHEST PRIORITY) ==========\n");
        prompt.append("TASK: ").append(currentTask.getTitle()).append("\n");
        if (currentTask.getRequirements() != null && !currentTask.getRequirements().isEmpty()) {
            prompt.append("REQUIREMENTS:\n");
            for (int i = 0; i < currentTask.getRequirements().size(); i++) {
                prompt.append(i + 1).append(". ").append(currentTask.getRequirements().get(i)).append("\n");
            }
        }
        prompt.append("======================================================\n");

        // Add RAG context if available
        if (!ragContext.isEmpty()) {
            prompt.append("\n").append(ragContext);
        }

        // Add instruction to include sources
        prompt.append("\n\nIMPORTANT: If you use information from the knowledge base, " +
                     "list the sources at the end of your response in this format:\n" +
                     "Information sources:\n" +
                     "1. [Source details]\n" +
                     "2. [Source details]");

        return prompt.toString();
    }

    /**
     * Stage 3: Evaluate task completion.
     */
    private TaskCompletionWithTokens performTaskCompletionEvaluation(
            String userMessage, String assistantAnswer, Task currentTask, String ragContext, LlmService llmService) {
        try {
            // Build context for evaluation
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("\n\nTASK TO EVALUATE:\n");
            contextBuilder.append("Title: ").append(currentTask.getTitle()).append("\n");
            if (currentTask.getRequirements() != null && !currentTask.getRequirements().isEmpty()) {
                contextBuilder.append("Requirements:\n");
                for (String req : currentTask.getRequirements()) {
                    contextBuilder.append("- ").append(req).append("\n");
                }
            }

            if (!ragContext.isEmpty()) {
                contextBuilder.append("\n\nKNOWLEDGE BASE CONTEXT:\n").append(ragContext);
            }

            // Create messages with user request and assistant answer
            List<LlmService.LlmMessage> messages = new ArrayList<>();
            messages.add(new LlmService.LlmMessage("user", userMessage));
            messages.add(new LlmService.LlmMessage("assistant", assistantAnswer));

            LlmResponse response = llmService.callTaskCompletionApi(
                ApiConstants.TASK_COMPLETION_SYSTEM_PROMPT + contextBuilder.toString(),
                messages
            );

            TaskCompletionStatus status = parseTaskCompletionStatus(response.getContent());
            TokenUsage tokens = new TokenUsage(response.getPromptTokens(), response.getCompletionTokens(), response.getTotalTokens());

            return new TaskCompletionWithTokens(status, tokens);

        } catch (Exception e) {
            logger.warn("Task completion evaluation failed: {}", e.getMessage());
            TaskCompletionStatus fallback = new TaskCompletionStatus();
            fallback.setCompleted(false);
            fallback.setReason("Evaluation failed due to an error.");
            return new TaskCompletionWithTokens(fallback, new TokenUsage(0, 0, 0));
        }
    }

    private TaskDecisionResponse parseTaskDecisionResponse(String content) {
        try {
            String jsonContent = extractJson(content);
            jsonContent = cleanJson(jsonContent);
            return objectMapper.readValue(jsonContent, TaskDecisionResponse.class);
        } catch (Exception e) {
            logger.warn("Failed to parse task decision response: {}", e.getMessage());
            return null;
        }
    }

    private TaskCompletionStatus parseTaskCompletionStatus(String content) {
        try {
            // Handle empty response
            if (content == null || content.trim().isEmpty()) {
                logger.warn("Task completion status response was empty");
                TaskCompletionStatus fallback = new TaskCompletionStatus();
                fallback.setCompleted(false);
                fallback.setReason("LLM returned empty response for completion evaluation.");
                return fallback;
            }

            String jsonContent = extractJson(content);
            jsonContent = cleanJson(jsonContent);
            TaskCompletionStatus status = objectMapper.readValue(jsonContent, TaskCompletionStatus.class);
            // Validate that we got a proper status
            if (status.getReason() == null || status.getReason().isEmpty()) {
                logger.warn("Task completion status had no reason");
            }
            return status;
        } catch (Exception e) {
            logger.warn("Failed to parse task completion status: {}", e.getMessage());
            // Try to extract completion info from non-JSON response
            return extractStatusFromText(content);
        }
    }

    /**
     * Fallback: extract completion status from non-JSON text response.
     */
    private TaskCompletionStatus extractStatusFromText(String content) {
        TaskCompletionStatus status = new TaskCompletionStatus();

        // Handle empty or whitespace-only content
        if (content == null || content.trim().isEmpty()) {
            status.setCompleted(false);
            status.setReason("Could not determine completion status - response was empty.");
            return status;
        }

        // Look for indicators of completion
        String lower = content.toLowerCase();
        boolean isCompleted = lower.contains("completed") ||
                              lower.contains("iscompleted") ||
                              lower.contains("\"true\"") ||
                              (lower.contains("true") && !lower.contains("false"));

        status.setCompleted(isCompleted);
        status.setReason(content.trim());

        return status;
    }

    private String extractJson(String content) {
        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }

        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");

        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }

        return content;
    }

    /**
     * Clean common JSON issues from smaller LLMs: trailing commas before } or ].
     */
    private String cleanJson(String json) {
        // Remove trailing commas before } or ]: ,} -> } and ,] -> ]
        json = json.replaceAll(",\\s*([}\\]])", "$1");
        return json;
    }

    private List<LlmService.LlmMessage> getHistoryForApi() {
        List<com.github.pvtitov.aichatclearning.model.ChatMessage> history = dialogHistoryRepository.findAll();
        List<LlmService.LlmMessage> apiMessages = new ArrayList<>();

        for (com.github.pvtitov.aichatclearning.model.ChatMessage msg : history) {
            apiMessages.add(new LlmService.LlmMessage(msg.getRole(), msg.getContent()));
        }

        return apiMessages;
    }

    private void printTaskDecision(Task task, boolean isNewTask) {
        System.out.println("=== Task Decision ===");
        System.out.println("Task: " + task.getTitle());
        System.out.println("Type: " + (isNewTask ? "NEW TASK" : "EXISTING TASK"));
        if (task.getRequirements() != null && !task.getRequirements().isEmpty()) {
            System.out.println("Requirements:");
            for (String req : task.getRequirements()) {
                System.out.println("  - " + req);
            }
        } else {
            System.out.println("Requirements: (none specified)");
        }
        System.out.println("=====================");
    }

    private void printTaskCompletionStatus(TaskCompletionStatus status) {
        System.out.println("=== Task Completion Status ===");
        if (status == null) {
            System.out.println("Completed: unknown (parsing failed)");
            System.out.println("Reason: could not parse LLM response");
        } else {
            System.out.println("Completed: " + status.isCompleted());
            System.out.println("Reason: " + status.getReason());
        }
        System.out.println("==============================");
    }

    private void printTokenSummary(TokenUsageTracker tracker) {
        System.out.println("\n========== Token Usage Summary ==========");
        System.out.println("Stage 1 (Task Decision):  Input: " + tracker.stage1Input +
                          ", Output: " + tracker.stage1Output +
                          ", Total: " + tracker.stage1Total);
        System.out.println("Stage 2 (Answer):         Input: " + tracker.stage2Input +
                          ", Output: " + tracker.stage2Output +
                          ", Total: " + tracker.stage2Total);
        System.out.println("Stage 3 (Completion):     Input: " + tracker.stage3Input +
                          ", Output: " + tracker.stage3Output +
                          ", Total: " + tracker.stage3Total);
        System.out.println("-------------------------------------------------");
        System.out.println("TOTAL:                    Input: " + tracker.input +
                          ", Output: " + tracker.output +
                          ", Total: " + tracker.total);
        System.out.println("============================================");
    }

    /**
     * Helper class to track token usage across all 3 stages.
     */
    private static class TokenUsageTracker {
        int stage1Input, stage1Output, stage1Total;
        int stage2Input, stage2Output, stage2Total;
        int stage3Input, stage3Output, stage3Total;
        int input, output, total;

        void add(int input, int output, int total) {
            if (stage1Input == 0 && stage1Output == 0 && stage1Total == 0) {
                stage1Input = input;
                stage1Output = output;
                stage1Total = total;
            } else if (stage2Input == 0 && stage2Output == 0 && stage2Total == 0) {
                stage2Input = input;
                stage2Output = output;
                stage2Total = total;
            } else {
                stage3Input = input;
                stage3Output = output;
                stage3Total = total;
            }
            this.input += input;
            this.output += output;
            this.total += total;
        }

        void add(TokenUsage tokens) {
            add(tokens.input, tokens.output, tokens.total);
        }
    }

    /**
     * Simple token usage record.
     */
    private static class TokenUsage {
        int input, output, total;

        TokenUsage(int input, int output, int total) {
            this.input = input;
            this.output = output;
            this.total = total;
        }
    }

    /**
     * Wrapper for task decision response with token usage.
     */
    private static class TaskDecisionWithTokens {
        TaskDecisionResponse response;
        TokenUsage tokens;

        TaskDecisionWithTokens(TaskDecisionResponse response, TokenUsage tokens) {
            this.response = response;
            this.tokens = tokens;
        }
    }

    /**
     * Wrapper for task completion status with token usage.
     */
    private static class TaskCompletionWithTokens {
        TaskCompletionStatus status;
        TokenUsage tokens;

        TaskCompletionWithTokens(TaskCompletionStatus status, TokenUsage tokens) {
            this.status = status;
            this.tokens = tokens;
        }
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
