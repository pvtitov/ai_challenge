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

    public EmbeddingSearchService getEmbeddingSearchService() {
        return embeddingSearchService;
    }

    public GitHubMcpService getGitHubMcpService() {
        return gitHubMcpService;
    }

    public DialogHistoryRepository getDialogHistoryRepository() {
        return dialogHistoryRepository;
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
                System.out.println("[GitHub MCP: ✓ Retrieved repository information (" + githubContext.length() + " bytes)]");
            }
        } else {
            System.out.println("[GitHub MCP: ✗ Server not available (run MCP server on localhost:8083)]");
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
     * Supports commits, branches, file reading, PRs, and general repo exploration.
     */
    private String tryGetGitHubContext(String userMessage) {
        StringBuilder context = new StringBuilder();
        String lowerMessage = userMessage.toLowerCase();

        // Check if user is asking about commits, log, or history
        if (lowerMessage.contains("commit") || lowerMessage.contains("log") ||
            lowerMessage.contains("history") || lowerMessage.contains("changed") ||
            lowerMessage.contains("last") || lowerMessage.contains("recent")) {
            List<Map<String, String>> commits = gitHubMcpService.getCommitHistory("main", 10);
            if (!commits.isEmpty()) {
                Map<String, String> firstCommit = commits.get(0);
                // Check if it's an error message
                if (!firstCommit.get("message").startsWith("[GitHub MCP:")) {
                    context.append("\n\nRECENT COMMITS FROM REPOSITORY (via MCP):\n");
                    context.append("Repository: ").append(ApiConstants.TINYAI_REPO_URL).append("\n\n");
                    for (Map<String, String> commit : commits) {
                        String hash = commit.getOrDefault("hash", "unknown");
                        String message = commit.getOrDefault("message", "no message");
                        String date = commit.getOrDefault("date", "");
                        String author = commit.getOrDefault("author", "");
                        context.append("- ").append(hash.substring(0, Math.min(7, hash.length())));
                        if (!date.isEmpty()) {
                            context.append(" (").append(date).append(")");
                        }
                        if (!author.isEmpty()) {
                            context.append(" by ").append(author);
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
                context.append("\n\nBRANCHES IN REPOSITORY (via MCP):\n");
                for (String branch : branches) {
                    context.append("- ").append(branch).append("\n");
                }
            }
        }

        // Check if user is asking about README or project overview
        if (lowerMessage.contains("readme") || lowerMessage.contains("read me") || 
            lowerMessage.contains("overview") || lowerMessage.contains("about")) {
            String readme = gitHubMcpService.readFile("README.md", "main");
            if (readme != null && !readme.startsWith("[GitHub MCP:")) {
                context.append("\n\nREADME.MD FROM REPOSITORY (via MCP):\n");
                // Truncate if too long to avoid context overflow
                if (readme.length() > 3000) {
                    readme = readme.substring(0, 3000) + "\n...[truncated]";
                }
                context.append(readme);
            }
        }

        // Check if asking about repository structure
        if (lowerMessage.contains("structure") || lowerMessage.contains("layout") || 
            lowerMessage.contains("architecture") || lowerMessage.contains("files")) {
            String structure = gitHubMcpService.getRepoStructure(3);
            if (structure != null && !structure.startsWith("[GitHub MCP:")) {
                context.append("\n\nREPOSITORY STRUCTURE (via MCP):\n");
                if (structure.length() > 2000) {
                    structure = structure.substring(0, 2000) + "\n...[truncated]";
                }
                context.append(structure);
            }
        }

        // Check if asking about a specific file
        if (lowerMessage.contains("file") && (lowerMessage.contains("show") || lowerMessage.contains("read") || 
            lowerMessage.contains("content") || lowerMessage.contains("what"))) {
            // Try to extract file name from query
            String[] commonFiles = {"README.md", "requirements.txt", "setup.py", "pom.xml", 
                                    "package.json", "build.gradle", "Makefile", "Dockerfile"};
            for (String fileName : commonFiles) {
                if (lowerMessage.contains(fileName.toLowerCase())) {
                    String content = gitHubMcpService.readFile(fileName, "main");
                    if (content != null && !content.startsWith("[GitHub MCP:")) {
                        context.append("\n\nCONTENTS OF ").append(fileName.toUpperCase()).append(" (via MCP):\n");
                        if (content.length() > 2500) {
                            content = content.substring(0, 2500) + "\n...[truncated]";
                        }
                        context.append(content);
                    }
                    break;
                }
            }
        }

        return context.toString();
    }
    
    /**
     * Build system prompt with RAG and GitHub context.
     * RAG context is given highest priority.
     */
    private String buildSystemPrompt(String ragContext, String githubContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(ApiConstants.ENHANCED_CHAT_SYSTEM_PROMPT);

        // Add RAG context FIRST (highest priority)
        if (!ragContext.isEmpty()) {
            prompt.append("═══ KNOWLEDGE BASE DATA (Use this as your PRIMARY source) ═══\n");
            prompt.append("The following information was retrieved from the project's knowledge base.\n");
            prompt.append("This contains project documentation, architecture guidelines, and requirements.\n");
            prompt.append("You MUST reference this when answering questions about the project.\n\n");
            prompt.append(ragContext);
            prompt.append("\n");
        }

        // Add GitHub context (second priority)
        if (!githubContext.isEmpty()) {
            prompt.append("═══ GITHUB REPOSITORY DATA (Use for specific code/commit/branch queries) ═══\n");
            prompt.append("The following information was retrieved from the GitHub repository.\n");
            prompt.append("Use this to provide accurate, factual answers about the codebase.\n\n");
            prompt.append(githubContext);
            prompt.append("\n");
        }

        // Final reminder
        prompt.append("═══ YOUR TASK ═══\n");
        prompt.append("Answer the user's question using the provided context above.\n");
        prompt.append("If knowledge base data is available, use it FIRST before relying on general knowledge.\n");
        prompt.append("If GitHub repository data is available, use it for specific code, commits, branches.\n");
        prompt.append("Always cite your sources and be specific about where information comes from.\n");

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
