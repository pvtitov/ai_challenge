package com.github.pvtitov.aichatlite;

import com.github.pvtitov.aichatlite.dto.EmbeddingSearchResult;
import com.github.pvtitov.aichatlite.dto.LlmStructuredResponse;
import com.github.pvtitov.aichatlite.service.ChatService;
import com.github.pvtitov.aichatlite.service.ChatServiceImpl;

import java.util.List;
import java.util.Scanner;

public class AichatLiteApplication {
    
    private static final String COMMAND_QUIT = "/quit";
    private static final String COMMAND_CLEAN = "/clean";
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  AI Chat Lite - Simple CLI Chat");
        System.out.println("========================================");
        System.out.println("Commands:");
        System.out.println("  " + COMMAND_QUIT + " - Exit the application");
        System.out.println("  " + COMMAND_CLEAN + " - Clear dialog history");
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
                
                try {
                    System.out.println("\nAI is thinking...");
                    LlmStructuredResponse response = chatService.processMessage(userInput);

                    // Print warning if JSON parsing failed
                    if (response.isJsonParseFailed()) {
                        System.out.println("\n[Warning: Failed to parse structured response. Showing raw response.]");
                    }

                    // Print response
                    System.out.println("\nAI: " + response.getResponse());

                    // Print token usage
                    if (response.getTokens() != null) {
                        System.out.println("\n[Tokens - Input: " + response.getTokens().getInput() +
                                         ", Output: " + response.getTokens().getOutput() +
                                         ", Total: " + response.getTokens().getTotal() + "]");
                    }

                    // Print RAG sources
                    if (response.getRagSources() != null && !response.getRagSources().isEmpty()) {
                        List<String> uniqueSources = response.getRagSources().stream()
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
}
