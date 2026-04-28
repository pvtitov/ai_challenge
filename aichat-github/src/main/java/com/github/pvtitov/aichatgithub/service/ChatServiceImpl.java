package com.github.pvtitov.aichatgithub.service;

import com.github.pvtitov.aichatgithub.constants.ApiConstants;
import com.github.pvtitov.aichatgithub.dto.EmbeddingSearchResult;
import com.github.pvtitov.aichatgithub.dto.LlmStructuredResponse;
import com.github.pvtitov.aichatgithub.model.ChatMessage;
import com.github.pvtitov.aichatgithub.repository.DialogHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simplified chat service - basic Q&A with RAG and GitHub MCP support.
 */
public class ChatServiceImpl implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final LlmServiceRegistry llmServiceRegistry;
    private final EmbeddingSearchService embeddingSearchService;
    private final GitHubMcpService gitHubMcpService;
    private final DialogHistoryRepository dialogHistoryRepository;

    public ChatServiceImpl() {
        this.llmServiceRegistry = new LlmServiceRegistry();
        this.embeddingSearchService = new EmbeddingSearchService();
        this.gitHubMcpService = new GitHubMcpService();
        this.dialogHistoryRepository = new DialogHistoryRepository();
        
        logger.info("ChatService initialized");
    }

    public LlmServiceRegistry getLlmServiceRegistry() {
        return llmServiceRegistry;
    }

    @Override
    public LlmStructuredResponse processMessage(String userMessage) {
        LlmService llmService = llmServiceRegistry.getCurrentService();
        
        // Save user message to history
        ChatMessage userMsg = new ChatMessage("user", userMessage);
        dialogHistoryRepository.save(userMsg);
        
        // === STAGE 1: Search for context ===
        System.out.println("\n[Searching for relevant context...]");
        
        // RAG search
        List<EmbeddingSearchResult> ragResults = List.of();
        String ragContext = "";
        
        if (embeddingSearchService.isReady()) {
            ragResults = embeddingSearchService.search(userMessage);
            System.out.println("[RAG: Found " + ragResults.size() + " relevant source(s) in knowledge base]");
            if (!ragResults.isEmpty()) {
                ragContext = embeddingSearchService.formatResultsAsContext(ragResults);
                System.out.println("[RAG: ✓ Using knowledge base context]");
            }
        } else {
            System.out.println("[RAG: ✗ Knowledge base not available]");
        }
        
        // GitHub MCP context
        String githubContext = "";
        boolean mcpUsed = false;
        
        if (gitHubMcpService.isReady()) {
            System.out.println("[GitHub MCP: ✓ Server available]");
            // Try to get relevant GitHub information
            githubContext = tryGetGitHubContext(userMessage);
            if (!githubContext.isEmpty()) {
                mcpUsed = true;
                System.out.println("[GitHub MCP: ✓ Retrieved repository information]");
            }
        } else {
            System.out.println("[GitHub MCP: ✗ Server not available (run MCP server on localhost:3000)]");
        }
        
        // === STAGE 2: Get answer from LLM ===
        System.out.println("\n[Getting response from LLM...]");
        
        String systemPrompt = buildSystemPrompt(ragContext, githubContext);
        List<LlmService.LlmMessage> history = getHistoryForApi();
        
        LlmResponse apiResponse = llmService.callChatApi(history, systemPrompt);
        
        // Save assistant response to history
        ChatMessage assistantMsg = new ChatMessage("assistant", apiResponse.getContent());
        assistantMsg.setPromptTokens(apiResponse.getPromptTokens());
        assistantMsg.setCompletionTokens(apiResponse.getCompletionTokens());
        assistantMsg.setTotalTokens(apiResponse.getTotalTokens());
        dialogHistoryRepository.save(assistantMsg);
        
        // Build structured response
        LlmStructuredResponse structuredResponse = new LlmStructuredResponse();
        structuredResponse.setResponse(apiResponse.getContent());
        structuredResponse.setRagSources(ragResults);
        structuredResponse.setJsonParseFailed(false);
        
        // Print AI response
        System.out.println("\nAI: " + apiResponse.getContent());
        
        // Print RAG sources if available
        if (!ragResults.isEmpty()) {
            System.out.println("\n━━━ RAG Sources Used ━━━");
            for (int i = 0; i < ragResults.size(); i++) {
                EmbeddingSearchResult result = ragResults.get(i);
                System.out.println((i + 1) + ". " + result.getSource());
                if (result.getTitle() != null && !result.getTitle().isEmpty()) {
                    System.out.println("   Title: " + result.getTitle());
                }
                if (result.getSection() != null && !result.getSection().isEmpty()) {
                    System.out.println("   Section: " + result.getSection());
                }
                System.out.println("   Similarity: " + String.format("%.2f", result.getSimilarityScore()));
            }
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━");
        }
        
        // Print MCP usage info
        if (mcpUsed) {
            System.out.println("\n━━━ GitHub MCP Used ━━━");
            System.out.println("✓ Retrieved information from tinyAI repository");
            System.out.println("  Repository: " + ApiConstants.TINYAI_REPO_URL);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━");
        }
        
        // Print token usage
        System.out.println("\n━━━ Token Usage ━━━");
        System.out.println("Input:  " + apiResponse.getPromptTokens());
        System.out.println("Output: " + apiResponse.getCompletionTokens());
        System.out.println("Total:  " + apiResponse.getTotalTokens());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━");
        
        // Set token usage in response
        LlmStructuredResponse.TokenUsage tokenUsage = new LlmStructuredResponse.TokenUsage();
        tokenUsage.setInput(apiResponse.getPromptTokens());
        tokenUsage.setOutput(apiResponse.getCompletionTokens());
        tokenUsage.setTotal(apiResponse.getTotalTokens());
        structuredResponse.setTokens(tokenUsage);
        
        return structuredResponse;
    }
    
    /**
     * Try to get relevant GitHub context based on user query.
     */
    private String tryGetGitHubContext(String userMessage) {
        StringBuilder context = new StringBuilder();
        String lowerMessage = userMessage.toLowerCase();
        
        // Check if user is asking about commits, log, or history
        if (lowerMessage.contains("commit") || lowerMessage.contains("log") || 
            lowerMessage.contains("history") || lowerMessage.contains("changed")) {
            List<Map<String, String>> commits = gitHubMcpService.getCommitHistory("main", 10);
            if (!commits.isEmpty()) {
                Map<String, String> firstCommit = commits.get(0);
                // Check if it's an error message
                if (!firstCommit.get("message").startsWith("[GitHub MCP:")) {
                    context.append("\n\nRECENT COMMITS FROM TINYAI REPOSITORY (via MCP):\n");
                    for (Map<String, String> commit : commits) {
                        String hash = commit.getOrDefault("hash", "unknown");
                        String message = commit.getOrDefault("message", "no message");
                        String date = commit.getOrDefault("date", "");
                        context.append("- ").append(hash.substring(0, Math.min(7, hash.length())));
                        if (!date.isEmpty()) {
                            context.append(" (").append(date).append(")");
                        }
                        context.append(": ").append(message).append("\n");
                    }
                }
            }
        }
        
        // Check if user is asking about branches
        if (lowerMessage.contains("branch")) {
            List<String> branches = gitHubMcpService.listBranches();
            if (!branches.isEmpty() && !branches.get(0).startsWith("[GitHub MCP:")) {
                context.append("\n\nBRANCHES IN TINYAI REPOSITORY (via MCP):\n");
                for (String branch : branches) {
                    context.append("- ").append(branch).append("\n");
                }
            }
        }
        
        // Check if user is asking about a specific file
        if (lowerMessage.contains("readme") || lowerMessage.contains("read me")) {
            String readme = gitHubMcpService.readFile("README.md", "main");
            if (readme != null && !readme.startsWith("[GitHub MCP:")) {
                context.append("\n\nREADME.MD FROM TINYAI REPOSITORY (via MCP):\n");
                // Truncate if too long
                if (readme.length() > 2000) {
                    readme = readme.substring(0, 2000) + "...[truncated]";
                }
                context.append(readme);
            }
        }
        
        // Check if asking about a specific commit
        if (lowerMessage.contains("commit ") && lowerMessage.matches(".*[a-f0-9]{7,}.*")) {
            // Extract commit hash
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([a-f0-9]{7,})").matcher(lowerMessage);
            if (matcher.find()) {
                String commitHash = matcher.group(1);
                context.append("\n\nLooking up commit: ").append(commitHash).append("\n");
                // The LLM will use this context to answer
            }
        }
        
        return context.toString();
    }
    
    /**
     * Build system prompt with RAG and GitHub context.
     */
    private String buildSystemPrompt(String ragContext, String githubContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful assistant specialized on the tinyAI GitHub project.\n");
        prompt.append("The tinyAI project is located at https://github.com/Headmast/tinyAI.git\n\n");
        prompt.append("Provide clear, accurate, and concise responses. ");
        prompt.append("If you have access to the knowledge base or GitHub repository, use that information to answer.\n");
        
        // Add RAG context if available
        if (!ragContext.isEmpty()) {
            prompt.append("\n═══ KNOWLEDGE BASE CONTEXT (from tinyAI embeddings) ═══\n");
            prompt.append(ragContext);
            prompt.append("\n");
        }
        
        // Add GitHub context if available
        if (!githubContext.isEmpty()) {
            prompt.append("\n═══ GITHUB REPOSITORY CONTEXT (from MCP server) ═══\n");
            prompt.append(githubContext);
            prompt.append("\n");
        }
        
        prompt.append("\nIMPORTANT: If you use information from the knowledge base or GitHub repository, ");
        prompt.append("mention it in your response and list the sources at the end.\n");
        
        return prompt.toString();
    }
    
    private List<LlmService.LlmMessage> getHistoryForApi() {
        List<ChatMessage> history = dialogHistoryRepository.findAll();
        List<LlmService.LlmMessage> apiMessages = new ArrayList<>();
        
        for (ChatMessage msg : history) {
            apiMessages.add(new LlmService.LlmMessage(msg.getRole(), msg.getContent()));
        }
        
        return apiMessages;
    }
    
    @Override
    public void clearHistory() {
        dialogHistoryRepository.deleteAll();
        logger.info("Dialog history cleared");
    }
    
    @Override
    public void shutdown() {
        dialogHistoryRepository.close();
        embeddingSearchService.close();
        gitHubMcpService.close();
    }
}
