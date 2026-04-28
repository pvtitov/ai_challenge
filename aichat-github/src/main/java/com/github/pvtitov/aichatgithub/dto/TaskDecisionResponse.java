package com.github.pvtitov.aichatgithub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response from the task decision LLM call (1st request).
 * Determines if task changed and what requirements were added.
 */
public class TaskDecisionResponse {

    private String taskTitle;
    private boolean isNewTask;
    private Long existingTaskId;
    private List<String> requirements;
    private List<String> addedRequirements;

    public TaskDecisionResponse() {
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
    }

    @JsonProperty("isNewTask")
    public boolean isNewTask() {
        return isNewTask;
    }

    @JsonProperty("isNewTask")
    public void setNewTask(boolean newTask) {
        isNewTask = newTask;
    }

    public Long getExistingTaskId() {
        return existingTaskId;
    }

    public void setExistingTaskId(Long existingTaskId) {
        this.existingTaskId = existingTaskId;
    }

    public List<String> getRequirements() {
        return requirements;
    }

    public void setRequirements(List<String> requirements) {
        this.requirements = requirements;
    }

    public List<String> getAddedRequirements() {
        return addedRequirements;
    }

    public void setAddedRequirements(List<String> addedRequirements) {
        this.addedRequirements = addedRequirements;
    }
}
