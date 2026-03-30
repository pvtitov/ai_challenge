package com.github.pvtitov.aichat.dto;

public class ChatResponse {
    private String agentResponse;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private long cumulativeTotalTokens;

    public ChatResponse(String agentResponse, int promptTokens, int completionTokens, int totalTokens, long cumulativeTotalTokens) {
        this.agentResponse = agentResponse;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.cumulativeTotalTokens = cumulativeTotalTokens;
    }

    // Getters and setters
    public String getAgentResponse() {
        return agentResponse;
    }

    public void setAgentResponse(String agentResponse) {
        this.agentResponse = agentResponse;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getCumulativeTotalTokens() {
        return cumulativeTotalTokens;
    }

    public void setCumulativeTotalTokens(long cumulativeTotalTokens) {
        this.cumulativeTotalTokens = cumulativeTotalTokens;
    }
}
