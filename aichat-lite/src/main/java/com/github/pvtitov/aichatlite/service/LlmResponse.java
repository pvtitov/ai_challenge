package com.github.pvtitov.aichatlite.service;

/**
 * Standardized response from any LLM provider.
 */
public class LlmResponse {
    private final String content;
    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;

    public LlmResponse(String content, int promptTokens, int completionTokens, int totalTokens) {
        this.content = content;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public String getContent() {
        return content;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }
}
