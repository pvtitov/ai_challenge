package com.github.pvtitov.aichatgithub;

import com.github.pvtitov.aichatgithub.dto.LlmStructuredResponse;
import com.github.pvtitov.aichatgithub.service.ChatService;
import com.github.pvtitov.aichatgithub.service.ChatServiceImpl;
import com.github.pvtitov.aichatgithub.service.CodeReviewService;
import com.github.pvtitov.aichatgithub.service.LlmModel;

import java.util.List;
import java.util.Scanner;

public class AichatGithubApplication {

    private static final String COMMAND_QUIT = "/quit";
    private static final String COMMAND_CLEAN = "/clean";
    private static final String COMMAND_MODEL = "/model";
    private static final String COMMAND_HELP = "/help";
    private static final String COMMAND_REVIEW = "/review";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  AI Chat GitHub - GitHub Agent");
        System.out.println("========================================");
        System.out.println("Commands:");
        System.out.println("  " + COMMAND_QUIT + " - Exit the application");
        System.out.println("  " + COMMAND_CLEAN + " - Clear dialog history");
        System.out.println("  " + COMMAND_MODEL + " - List available models");
        System.out.println("  " + COMMAND_MODEL + " <model> - Switch to a model");
        System.out.println("  " + COMMAND_REVIEW + " <target> - Review code (commit, PR, branch, project)");
        System.out.println("  " + COMMAND_HELP + " - Show project structure or ask about tinyAI");
        System.out.println("\nExamples:");
        System.out.println("  " + COMMAND_REVIEW + " last commit");
        System.out.println("  " + COMMAND_REVIEW + " PR #123");
        System.out.println("  " + COMMAND_REVIEW + " main branch");
        System.out.println("  " + COMMAND_REVIEW + " entire project");
        System.out.println("========================================\n");

        ChatService chatService = new ChatServiceImpl();
        Scanner scanner = new Scanner(System.in);

        try {
            while (true) {
                System.out.print("You: ");
                String userInput = scanner.nextLine().trim();

                if (userInput.isEmpty()) {
                    continue;
                }

                if (COMMAND_QUIT.equalsIgnoreCase(userInput)) {
                    System.out.println("Goodbye!");
                    break;
                }

                if (COMMAND_CLEAN.equalsIgnoreCase(userInput)) {
                    chatService.clearHistory();
                    System.out.println("Dialog history cleared.\n");
                    continue;
                }

                if (userInput.equalsIgnoreCase(COMMAND_MODEL)) {
                    printAvailableModels(chatService);
                    continue;
                }

                if (userInput.toLowerCase().startsWith(COMMAND_MODEL + " ")) {
                    String modelName = userInput.substring(COMMAND_MODEL.length()).trim();
                    switchModel(chatService, modelName);
                    continue;
                }

                if (userInput.toLowerCase().startsWith(COMMAND_HELP)) {
                    String helpQuery = userInput.length() > COMMAND_HELP.length()
                        ? userInput.substring(COMMAND_HELP.length()).trim()
                        : "";
                    handleHelp(chatService, helpQuery);
                    continue;
                }

                if (userInput.toLowerCase().equals(COMMAND_REVIEW)) {
                    // /review without arguments - default to reviewing the entire project
                    handleReview(chatService, "entire project");
                    continue;
                }

                if (userInput.toLowerCase().startsWith(COMMAND_REVIEW + " ")) {
                    String reviewTarget = userInput.substring(COMMAND_REVIEW.length()).trim();
                    handleReview(chatService, reviewTarget);
                    continue;
                }

                try {
                    LlmStructuredResponse response = chatService.processMessage(userInput);

                    // Print warning if JSON parsing failed
                    if (response.isJsonParseFailed()) {
                        System.out.println("\n[Warning: Failed to parse structured response.]");
                    }

                    System.out.println(); // Empty line for readability

                } catch (Exception e) {
                    System.err.println("Error processing request: " + e.getMessage());
                    e.printStackTrace();
                    System.out.println();
                }
            }
        } finally {
            chatService.shutdown();
            scanner.close();
        }
    }

    private static void printAvailableModels(ChatService chatService) {
        List<LlmModel> models = chatService.getLlmServiceRegistry().getAvailableModels();
        String currentModelId = chatService.getLlmServiceRegistry().getCurrentModelId();

        System.out.println("\n=== Available Models ===");
        if (models.isEmpty()) {
            System.out.println("No models available.");
        } else {
            for (LlmModel model : models) {
                String marker = model.getId().equals(currentModelId) ? " [CURRENT]" : "";
                System.out.println("  - " + model.getId() + ": " + model.getName() + marker);
            }
        }
        System.out.println("========================\n");
    }

    private static void switchModel(ChatService chatService, String modelName) {
        boolean switched = chatService.getLlmServiceRegistry().switchToModelByName(modelName);
        if (switched) {
            String currentModelId = chatService.getLlmServiceRegistry().getCurrentModelId();
            System.out.println("Switched to model: " + currentModelId);
        } else {
            // Try switching by ID
            switched = chatService.getLlmServiceRegistry().setCurrentModel(modelName);
            if (switched) {
                System.out.println("Switched to model: " + modelName);
            } else {
                System.out.println("Model '" + modelName + "' not found. Use /model to see available models.");
            }
        }
        System.out.println();
    }

    private static void handleHelp(ChatService chatService, String helpQuery) {
        if (helpQuery.isEmpty()) {
            // Print tinyAI project structure
            System.out.println("\n=== tinyAI Project Structure ===");
            System.out.println("Repository: https://github.com/Headmast/tinyAI.git");
            System.out.println();
            System.out.println("Main files and directories:");
            System.out.println("  - README.md          - Project documentation");
            System.out.println("  - requirements.txt   - Project dependencies");
            System.out.println("  - src/               - Source code directory");
            System.out.println("    - main/            - Main application code");
            System.out.println("    - test/            - Test files");
            System.out.println("  - embeddings.db      - Vector database with indexed chunks");
            System.out.println();
            System.out.println("Use /help <your question> to ask about this project.");
            System.out.println("Example: /help How MCP is used in tinyAI");
            System.out.println("===============================\n");
        } else {
            // Process with RAG to answer question about tinyAI
            System.out.println("\n[Searching knowledge base for: " + helpQuery + "]");
            try {
                LlmStructuredResponse response = chatService.processMessage(
                    "Based on the tinyAI project knowledge base, please answer: " + helpQuery
                );
                if (response.isJsonParseFailed()) {
                    System.out.println("\n[Warning: Failed to parse structured response.]");
                }
                System.out.println();
            } catch (Exception e) {
                System.err.println("Error processing help request: " + e.getMessage());
                e.printStackTrace();
                System.out.println();
            }
        }
    }

    private static void handleReview(ChatService chatService, String reviewTarget) {
        System.out.println("\n=== Code Review Mode ===");
        System.out.println("Target: " + reviewTarget);
        System.out.println();

        try {
            // Create review service using same dependencies as chat service
            CodeReviewService reviewService = new CodeReviewService(
                chatService.getLlmServiceRegistry(),
                ((ChatServiceImpl) chatService).getEmbeddingSearchService(),
                ((ChatServiceImpl) chatService).getGitHubMcpService(),
                ((ChatServiceImpl) chatService).getDialogHistoryRepository()
            );

            LlmStructuredResponse review = reviewService.performReview(reviewTarget);

            System.out.println("\n" + review.getResponse());

            // Print token usage
            if (review.getTokens() != null) {
                System.out.println("\n━━━ Token Usage ━━━");
                System.out.println("Input:  " + review.getTokens().getInput());
                System.out.println("Output: " + review.getTokens().getOutput());
                System.out.println("Total:  " + review.getTokens().getTotal());
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━");
            }

            System.out.println("\n=== Review Complete ===\n");

        } catch (Exception e) {
            System.err.println("Error during review: " + e.getMessage());
            e.printStackTrace();
            System.out.println();
        }
    }
}
