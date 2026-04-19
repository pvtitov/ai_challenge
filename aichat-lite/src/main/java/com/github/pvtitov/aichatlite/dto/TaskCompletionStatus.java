package com.github.pvtitov.aichatlite.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from the task completion status LLM call (3rd request).
 * Determines if the current task is completed.
 */
public class TaskCompletionStatus {

    private boolean isCompleted;
    private String reason;

    public TaskCompletionStatus() {
    }

    @JsonProperty("isCompleted")
    public boolean isCompleted() {
        return isCompleted;
    }

    @JsonProperty("isCompleted")
    public void setCompleted(boolean isCompleted) {
        this.isCompleted = isCompleted;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
