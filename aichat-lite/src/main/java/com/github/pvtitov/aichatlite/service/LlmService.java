package com.github.pvtitov.aichatlite.service;

import java.util.List;

/**
 * Interface for LLM service implementations.
 * All LLM providers (GigaChat, Ollama, etc.) must implement this interface.
 */
public interface LlmService {
    
    /**
     * Get the model this service is configured for.
     */
    LlmModel getModel();
    
    /**
     * Call the LLM chat API for getting an answer.
     * 
     * @param messages conversation history
     * @param systemPrompt system prompt
     * @return LLM response
     */
    LlmResponse callChatApi(List<LlmMessage> messages, String systemPrompt);
    
    /**
     * Call the LLM API for task decision (Stage 1).
     * Determines task changes and requirement additions.
     * 
     * @param systemPrompt system prompt
     * @param messages conversation history
     * @return LLM response
     */
    LlmResponse callTaskDecisionApi(String systemPrompt, List<LlmMessage> messages);
    
    /**
     * Call the LLM API for task completion status (Stage 3).
     * Evaluates whether the task is completed.
     * 
     * @param systemPrompt system prompt
     * @param messages conversation history
     * @return LLM response
     */
    LlmResponse callTaskCompletionApi(String systemPrompt, List<LlmMessage> messages);
    
    /**
     * Message representation for LLM communication.
     */
    class LlmMessage {
        private final String role;
        private final String content;

        public LlmMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }
}
