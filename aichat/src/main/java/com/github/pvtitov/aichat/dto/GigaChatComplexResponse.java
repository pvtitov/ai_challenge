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

    public GigaChatComplexResponse() {
    }

    public GigaChatComplexResponse(String fullResponse, String summary, String stickyFacts) {
        this.fullResponse = fullResponse;
        this.summary = summary;
        this.stickyFacts = stickyFacts;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GigaChatComplexResponse that = (GigaChatComplexResponse) o;
        return Objects.equals(fullResponse, that.fullResponse) &&
                Objects.equals(summary, that.summary) &&
                Objects.equals(stickyFacts, that.stickyFacts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullResponse, summary, stickyFacts);
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{" +
                    "\"full_response\":\"" + fullResponse + "\"," +
                    "\"summary\":\"" + summary + "\"," +
                    "\"sticky_facts\":\"" + stickyFacts + "\"" +
                    "}";
        }
    }
}

