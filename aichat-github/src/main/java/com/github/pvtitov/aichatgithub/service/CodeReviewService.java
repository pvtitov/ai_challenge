package com.github.pvtitov.aichatgithub.service;

import com.github.pvtitov.aichatgithub.constants.ApiConstants;
import com.github.pvtitov.aichatgithub.dto.EmbeddingSearchResult;
import com.github.pvtitov.aichatgithub.dto.LlmStructuredResponse;
import com.github.pvtitov.aichatgithub.model.ChatMessage;
import com.github.pvtitov.aichatgithub.repository.DialogHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Code review service that combines RAG knowledge and GitHub MCP data
 * to provide comprehensive code reviews.
 */
public class CodeReviewService {

    private static final Logger logger = LoggerFactory.getLogger(CodeReviewService.class);

    private final LlmServiceRegistry llmServiceRegistry;
    private final EmbeddingSearchService embeddingSearchService;
    private final GitHubMcpService gitHubMcpService;
    private final DialogHistoryRepository dialogHistoryRepository;

    public CodeReviewService(LlmServiceRegistry llmServiceRegistry,
                             EmbeddingSearchService embeddingSearchService,
                             GitHubMcpService gitHubMcpService,
                             DialogHistoryRepository dialogHistoryRepository) {
        this.llmServiceRegistry = llmServiceRegistry;
        this.embeddingSearchService = embeddingSearchService;
        this.gitHubMcpService = gitHubMcpService;
        this.dialogHistoryRepository = dialogHistoryRepository;
    }

    /**
     * Perform a comprehensive code review.
     * 
     * @param reviewTarget The target to review (e.g., "last commit", "PR #123", "main branch", "entire project")
     * @return Structured review response
     */
    public LlmStructuredResponse performReview(String reviewTarget) {
        LlmService llmService = llmServiceRegistry.getCurrentService();

        System.out.println("\n═══ CODE REVIEW MODE ═══");
        System.out.println("Reviewing: " + reviewTarget);
        System.out.println();

        // Step 1: Gather project context from RAG
        System.out.println("[Step 1: Gathering project context from knowledge base...]");
        String projectContext = gatherProjectContext();
        if (!projectContext.isEmpty()) {
            System.out.println("[✓ Project context loaded from RAG]");
        } else {
            System.out.println("[⚠ Limited project context available]");
        }

        // Step 2: Gather review target data from GitHub MCP
        System.out.println("[Step 2: Fetching review target from GitHub MCP...]");
        String reviewData = gatherReviewData(reviewTarget);
        if (!reviewData.isEmpty()) {
            System.out.println("[✓ Review data loaded from GitHub]");
        } else {
            System.out.println("[✗ Failed to fetch review data]");
        }

        // Step 3: Build review context
        String reviewContext = buildReviewContext(projectContext, reviewData);

        // Step 4: Generate review using LLM
        System.out.println("[Step 3: Generating comprehensive review...]");
        String systemPrompt = buildReviewSystemPrompt();
        List<LlmService.LlmMessage> messages = List.of(
            new LlmService.LlmMessage("user", 
                "Please review: " + reviewTarget + "\n\n" +
                "Use the provided context to analyze the code for bugs, architecture issues, " +
                "and provide actionable recommendations."
            )
        );

        LlmResponse apiResponse = llmService.callChatApi(messages, systemPrompt + reviewContext);

        // Save to history
        ChatMessage userMsg = new ChatMessage("user", "[CODE REVIEW] " + reviewTarget);
        dialogHistoryRepository.save(userMsg);
        
        ChatMessage assistantMsg = new ChatMessage("assistant", apiResponse.getContent());
        assistantMsg.setPromptTokens(apiResponse.getPromptTokens());
        assistantMsg.setCompletionTokens(apiResponse.getCompletionTokens());
        assistantMsg.setTotalTokens(apiResponse.getTotalTokens());
        dialogHistoryRepository.save(assistantMsg);

        // Build response
        LlmStructuredResponse response = new LlmStructuredResponse();
        response.setResponse(apiResponse.getContent());
        
        LlmStructuredResponse.TokenUsage tokenUsage = new LlmStructuredResponse.TokenUsage();
        tokenUsage.setInput(apiResponse.getPromptTokens());
        tokenUsage.setOutput(apiResponse.getCompletionTokens());
        tokenUsage.setTotal(apiResponse.getTotalTokens());
        response.setTokens(tokenUsage);

        return response;
    }

    /**
     * Gather project context from RAG knowledge base.
     */
    private String gatherProjectContext() {
        StringBuilder context = new StringBuilder();

        // Search for general project information
        List<EmbeddingSearchResult> results = embeddingSearchService.search("project overview architecture requirements");
        
        if (!results.isEmpty()) {
            context.append("PROJECT CONTEXT (from knowledge base):\n\n");
            for (EmbeddingSearchResult result : results) {
                if (result.getTitle() != null) {
                    context.append("### ").append(result.getTitle());
                    if (result.getSection() != null) {
                        context.append(" - ").append(result.getSection());
                    }
                    context.append("\n");
                }
                context.append(result.getContent()).append("\n\n");
            }
        }

        // Also search for code style and guidelines
        List<EmbeddingSearchResult> styleResults = embeddingSearchService.search("code style guidelines conventions");
        if (!styleResults.isEmpty()) {
            context.append("CODE STYLE AND GUIDELINES (from knowledge base):\n\n");
            for (EmbeddingSearchResult result : styleResults) {
                context.append(result.getContent()).append("\n\n");
            }
        }

        return context.toString();
    }

    /**
     * Gather review target data from GitHub MCP.
     */
    private String gatherReviewData(String reviewTarget) {
        StringBuilder data = new StringBuilder();
        String lowerTarget = reviewTarget.toLowerCase();

        // Review entire project
        if (lowerTarget.contains("project") || lowerTarget.contains("entire") || lowerTarget.contains("repo")) {
            // Get repository structure
            String structure = gitHubMcpService.getRepoStructure(3);
            if (!structure.startsWith("[GitHub MCP:")) {
                data.append("REPOSITORY STRUCTURE:\n").append(structure).append("\n\n");
            }

            // Get README
            String readme = gitHubMcpService.readFile("README.md", "main");
            if (!readme.startsWith("[GitHub MCP:")) {
                if (readme.length() > 2000) {
                    readme = readme.substring(0, 2000) + "...[truncated]";
                }
                data.append("README:\n").append(readme).append("\n\n");
            }

            // Get recent commits
            List<Map<String, String>> commits = gitHubMcpService.getCommitHistory("main", 5);
            if (!commits.isEmpty() && !commits.get(0).get("message").startsWith("[GitHub MCP:")) {
                data.append("RECENT COMMITS:\n");
                for (Map<String, String> commit : commits) {
                    data.append("- ").append(commit.get("hash").substring(0, 7));
                    data.append(": ").append(commit.get("message")).append("\n");
                }
            }
        }

        // Review last commit
        if (lowerTarget.contains("last commit") || lowerTarget.contains("recent commit") || 
            lowerTarget.contains("latest commit")) {
            List<Map<String, String>> commits = gitHubMcpService.getCommitHistory("main", 1);
            if (!commits.isEmpty() && !commits.get(0).get("message").startsWith("[GitHub MCP:")) {
                Map<String, String> commit = commits.get(0);
                data.append("LAST COMMIT:\n");
                data.append("Hash: ").append(commit.get("hash")).append("\n");
                data.append("Author: ").append(commit.get("author")).append("\n");
                data.append("Date: ").append(commit.get("date")).append("\n");
                data.append("Message: ").append(commit.get("message")).append("\n");
            }
        }

        // Review specific commit
        if (lowerTarget.contains("commit") && lowerTarget.matches(".*[a-f0-9]{7,}.*")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([a-f0-9]{7,})").matcher(lowerTarget);
            if (matcher.find()) {
                String commitHash = matcher.group(1);
                data.append("COMMIT: ").append(commitHash).append("\n");
                // Note: Current MCP doesn't support getting commit diff, but we can note it
                data.append("(Commit hash provided - full diff viewing requires additional MCP tools)\n\n");
            }
        }

        // Review branch
        if (lowerTarget.contains("branch")) {
            List<String> branches = gitHubMcpService.listBranches();
            if (!branches.isEmpty() && !branches.get(0).startsWith("[GitHub MCP:")) {
                data.append("AVAILABLE BRANCHES:\n");
                for (String branch : branches) {
                    data.append("- ").append(branch).append("\n");
                }
                data.append("\n");
            }

            // If specific branch mentioned
            if (lowerTarget.contains("main") || lowerTarget.contains("master")) {
                data.append("Reviewing main/master branch structure and recent changes.\n");
            }
        }

        // Review PR
        if (lowerTarget.contains("pr") || lowerTarget.contains("pull request")) {
            // Try to extract PR number
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:pr|pull request)\\s*#?(\\d+)", 
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(lowerTarget);
            if (matcher.find()) {
                String prNumber = matcher.group(1);
                data.append("PULL REQUEST #").append(prNumber).append("\n");
                data.append("(PR details would be fetched - requires PR-specific MCP tools)\n\n");
            } else {
                // List open PRs
                data.append("OPEN PULL REQUESTS:\n");
                data.append("(Use specific PR number to review, e.g., 'Review PR #123')\n\n");
            }
        }

        // Review specific file
        if (lowerTarget.contains("file") || lowerTarget.endsWith(".java") || lowerTarget.endsWith(".py") ||
            lowerTarget.endsWith(".js") || lowerTarget.endsWith(".ts")) {
            String[] extensions = {".java", ".py", ".js", ".ts", ".md", ".txt", ".xml", ".json", ".yml", ".yaml"};
            for (String ext : extensions) {
                if (lowerTarget.contains(ext)) {
                    int extIndex = lowerTarget.lastIndexOf(ext);
                    int spaceBefore = lowerTarget.lastIndexOf(' ', extIndex);
                    String fileName = lowerTarget.substring(spaceBefore + 1).trim();
                    
                    String content = gitHubMcpService.readFile(fileName, "main");
                    if (!content.startsWith("[GitHub MCP:")) {
                        if (content.length() > 3000) {
                            content = content.substring(0, 3000) + "\n...[truncated]";
                        }
                        data.append("FILE CONTENTS: ").append(fileName).append("\n\n");
                        data.append(content).append("\n\n");
                    }
                    break;
                }
            }
        }

        return data.toString();
    }

    /**
     * Build combined review context.
     */
    private String buildReviewContext(String projectContext, String reviewData) {
        StringBuilder context = new StringBuilder();
        context.append(ApiConstants.CODE_REVIEW_SYSTEM_PROMPT);
        
        if (!projectContext.isEmpty()) {
            context.append("\n\n═══ PROJECT CONTEXT ═══\n\n");
            context.append(projectContext);
        }
        
        if (!reviewData.isEmpty()) {
            context.append("\n\n═══ REVIEW TARGET DATA ═══\n\n");
            context.append(reviewData);
        }
        
        return context.toString();
    }

    /**
     * Build system prompt for code review.
     */
    private String buildReviewSystemPrompt() {
        return ApiConstants.CODE_REVIEW_SYSTEM_PROMPT;
    }
}
