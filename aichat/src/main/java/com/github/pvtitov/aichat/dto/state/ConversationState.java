package com.github.pvtitov.aichat.dto.state;

public class ConversationState {
    private Stage stage = Stage.AWAITING_PROMPT;
    private String originalPrompt;
    private String lastPlan;

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

    public void reset() {
        this.stage = Stage.AWAITING_PROMPT;
        this.originalPrompt = null;
        this.lastPlan = null;
    }
}
