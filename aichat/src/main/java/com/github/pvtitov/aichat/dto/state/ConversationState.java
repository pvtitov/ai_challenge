package com.github.pvtitov.aichat.dto.state;

public class ConversationState {
    private Stage stage = Stage.AWAITING_PROMPT;
    private String originalPrompt;
    private String lastPlan;
    private String lastActionResult;
    private boolean saveKnowledgeRequested = false;

    public Stage getStage() {
        return stage;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public String getOriginalPrompt() {
        return originalPrompt;
    }

    public void setOriginalPrompt(String originalPrompt) {
        this.originalPrompt = originalPrompt;
    }

    public String getLastPlan() {
        return lastPlan;
    }

    public void setLastPlan(String lastPlan) {
        this.lastPlan = lastPlan;
    }

    public String getLastActionResult() {
        return lastActionResult;
    }

    public void setLastActionResult(String lastActionResult) {
        this.lastActionResult = lastActionResult;
    }

    public boolean isSaveKnowledgeRequested() {
        return saveKnowledgeRequested;
    }

    public void setSaveKnowledgeRequested(boolean saveKnowledgeRequested) {
        this.saveKnowledgeRequested = saveKnowledgeRequested;
    }

    public void reset() {
        this.stage = Stage.AWAITING_PROMPT;
        this.originalPrompt = null;
        this.lastPlan = null;
        this.lastActionResult = null;
        this.saveKnowledgeRequested = false;
    }
}
