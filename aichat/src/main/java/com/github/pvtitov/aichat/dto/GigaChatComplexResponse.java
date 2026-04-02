package com.github.pvtitov.aichat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

public class GigaChatComplexResponse {
    @JsonProperty("full_response")
    private String fullResponse;
    @JsonProperty("summary")
    private String summary;
    @JsonProperty("sticky_facts")
    private String stickyFacts;
    @JsonProperty("prompt_tokens")
    private Integer promptTokens;
    @JsonProperty("completion_tokens")
    private Integer completionTokens;
    @JsonProperty("total_tokens")
    private Integer totalTokens;

    public GigaChatComplexResponse() {
    }

    public GigaChatComplexResponse(String fullResponse, String summary, String stickyFacts, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        this.fullResponse = fullResponse;
        this.summary = summary;
        this.stickyFacts = stickyFacts;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
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

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GigaChatComplexResponse that = (GigaChatComplexResponse) o;
        return Objects.equals(fullResponse, that.fullResponse) &&
                Objects.equals(summary, that.summary) &&
                Objects.equals(stickyFacts, that.stickyFacts) &&
                Objects.equals(promptTokens, that.promptTokens) &&
                Objects.equals(completionTokens, that.completionTokens) &&
                Objects.equals(totalTokens, that.totalTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullResponse, summary, stickyFacts, promptTokens, completionTokens, totalTokens);
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{" +
                    "\"full_response\":\"" + fullResponse + "\"," +
                    "\"summary\":\"" + summary + "\"," +
                    "\"sticky_facts\":\"" + stickyFacts + "\"," +
                    "\"prompt_tokens\":" + promptTokens + "," +
                    "\"completion_tokens\":" + completionTokens + "," +
                    "\"total_tokens\":" + totalTokens +
                    "}";
        }
    }
}

