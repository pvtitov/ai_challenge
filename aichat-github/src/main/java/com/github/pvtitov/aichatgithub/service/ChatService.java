package com.github.pvtitov.aichatgithub.service;

import com.github.pvtitov.aichatgithub.dto.LlmStructuredResponse;
import com.github.pvtitov.aichatgithub.repository.DialogHistoryRepository;

public interface ChatService {

    /**
     * Process user message and return structured response
     * @param userMessage the user's input message
     * @return structured response from LLM
     */
    LlmStructuredResponse processMessage(String userMessage);

    /**
     * Clear all dialog history
     */
    void clearHistory();

    /**
     * Shutdown and cleanup resources
     */
    void shutdown();

    /**
     * Get the LLM service registry for model management
     */
    LlmServiceRegistry getLlmServiceRegistry();

    /**
     * Get the embedding search service
     */
    EmbeddingSearchService getEmbeddingSearchService();

    /**
     * Get the GitHub MCP service
     */
    GitHubMcpService getGitHubMcpService();

    /**
     * Get the dialog history repository
     */
    DialogHistoryRepository getDialogHistoryRepository();
}
