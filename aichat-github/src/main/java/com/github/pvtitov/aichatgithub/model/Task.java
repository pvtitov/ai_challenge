package com.github.pvtitov.aichatgithub.model;

import com.github.pvtitov.aichatgithub.dto.TaskCompletionStatus;

import java.util.List;

public class Task {

    private Long id;
    private String title;
    private List<String> requirements;
    private TaskCompletionStatus status;

    public Task() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getRequirements() {
        return requirements;
    }

    public void setRequirements(List<String> requirements) {
        this.requirements = requirements;
    }

    public TaskCompletionStatus getStatus() {
        return status;
    }

    public void setStatus(TaskCompletionStatus status) {
        this.status = status;
    }
}
