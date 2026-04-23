package com.github.pvtitov.aichatclearning.service;

import com.github.pvtitov.aichatclearning.dto.LlmStructuredResponse;

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
}
