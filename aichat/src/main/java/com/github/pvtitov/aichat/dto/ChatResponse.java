package com.github.pvtitov.aichat.dto;

import java.util.Objects;

public class ChatResponse {
    private String fullResponse;
    private String summary;
    private String stickyFacts;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private long cumulativeTokens;
    private ResponseType responseType = ResponseType.FINAL;
    private boolean requiresConfirmation = false;

    public ChatResponse() {
    }

    public ChatResponse(String fullResponse, String summary, String stickyFacts, int promptTokens, int completionTokens, int totalTokens, long cumulativeTokens) {
        this.fullResponse = fullResponse;
        this.summary = summary;
        this.stickyFacts = stickyFacts;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.cumulativeTokens = cumulativeTokens;
    }
    
    public ChatResponse(String infoMessage) {
        this.fullResponse = infoMessage;
        this.responseType = ResponseType.INFO;
        this.requiresConfirmation = false;
    }

    public String getFullResponse() {
        return fullResponse;
    }

    public void setFullResponse(String fullResponse) {
        this.fullResponse = fullResponse;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getStickyFacts() {
        return stickyFacts;
    }

    public void setStickyFacts(String stickyFacts) {
        this.stickyFacts = stickyFacts;
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

    public long getCumulativeTokens() {
        return cumulativeTokens;
    }

    public void setCumulativeTokens(long cumulativeTokens) {
        this.cumulativeTokens = cumulativeTokens;
    }

    public ResponseType getResponseType() {
        return responseType;
    }

    public void setResponseType(ResponseType responseType) {
        this.responseType = responseType;
    }

    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }

    public void setRequiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatResponse that = (ChatResponse) o;
        return promptTokens == that.promptTokens &&
                completionTokens == that.completionTokens &&
                totalTokens == that.totalTokens &&
                cumulativeTokens == that.cumulativeTokens &&
                requiresConfirmation == that.requiresConfirmation &&
                Objects.equals(fullResponse, that.fullResponse) &&
                Objects.equals(summary, that.summary) &&
                Objects.equals(stickyFacts, that.stickyFacts) &&
                responseType == that.responseType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullResponse, summary, stickyFacts, promptTokens, completionTokens, totalTokens, cumulativeTokens, responseType, requiresConfirmation);
    }

    @Override
    public String toString() {
        return "ChatResponse{" +
                "fullResponse='" + fullResponse + '\'' +
                ", summary='" + summary + '\'' +
                ", stickyFacts='" + stickyFacts + '\'' +
                ", promptTokens=" + promptTokens +
                ", completionTokens=" + completionTokens +
                ", totalTokens=" + totalTokens +
                ", cumulativeTokens=" + cumulativeTokens +
                ", responseType=" + responseType +
                ", requiresConfirmation=" + requiresConfirmation +
                '}';
    }
}
