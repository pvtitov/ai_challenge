package com.github.pvtitov.aichatlite;

import com.github.pvtitov.aichatlite.dto.LlmStructuredResponse;
import com.github.pvtitov.aichatlite.service.ChatService;
import com.github.pvtitov.aichatlite.service.ChatServiceImpl;
import com.github.pvtitov.aichatlite.service.LlmModel;

import java.util.List;
import java.util.Scanner;

public class AichatLiteApplication {

    private static final String COMMAND_QUIT = "/quit";
    private static final String COMMAND_CLEAN = "/clean";
    private static final String COMMAND_MODEL = "/model";

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  AI Chat Lite - Simple CLI Chat");
        System.out.println("========================================");
        System.out.println("Commands:");
        System.out.println("  " + COMMAND_QUIT + " - Exit the application");
        System.out.println("  " + COMMAND_CLEAN + " - Clear dialog history");
        System.out.println("  " + COMMAND_MODEL + " - List available models");
        System.out.println("  " + COMMAND_MODEL + " <model> - Switch to a model");
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
}
