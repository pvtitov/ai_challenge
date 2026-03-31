package com.github.pvtitov.aichat.dto;

public class GigaChatComplexResponse {
    private String fullResponse;
    private String summary;
    private String stickyFacts;

    public GigaChatComplexResponse(String fullResponse, String summary, String stickyFacts) {
        this.fullResponse = fullResponse;
        this.summary = summary;
        this.stickyFacts = stickyFacts;
    }

    public String getFullResponse() {
        return fullResponse;
    }

    public String getSummary() {
        return summary;
    }

    public String getStickyFacts() {
        return stickyFacts;
    }
}
