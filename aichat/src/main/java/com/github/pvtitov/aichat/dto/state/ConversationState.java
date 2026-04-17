package com.github.pvtitov.aichat.dto.state;

import com.github.pvtitov.aichat.dto.CitationSource;

import java.util.ArrayList;
import java.util.List;

public class ConversationState {
    private Stage stage = Stage.AWAITING_PROMPT;
    private String originalPrompt;
    private String lastPlan;
    private String lastActionResult;
    private boolean saveKnowledgeRequested = false;
    
    // RAG-specific fields
    private List<CitationSource> ragCitations = new ArrayList<>();
    private double maxRelevanceScore = 0.0;
    private boolean lowRelevanceContext = false;

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

    // RAG-specific getters and setters
    
    public List<CitationSource> getRagCitations() {
        return ragCitations;
    }

    public void setRagCitations(List<CitationSource> ragCitations) {
        this.ragCitations = ragCitations;
    }

    public void addRagCitation(CitationSource citation) {
        this.ragCitations.add(citation);
    }

    public double getMaxRelevanceScore() {
        return maxRelevanceScore;
    }

    public void setMaxRelevanceScore(double maxRelevanceScore) {
        this.maxRelevanceScore = maxRelevanceScore;
    }

    public boolean isLowRelevanceContext() {
        return lowRelevanceContext;
    }

    public void setLowRelevanceContext(boolean lowRelevanceContext) {
        this.lowRelevanceContext = lowRelevanceContext;
    }

    public void reset() {
        this.stage = Stage.AWAITING_PROMPT;
        this.originalPrompt = null;
        this.lastPlan = null;
        this.lastActionResult = null;
        this.saveKnowledgeRequested = false;
        this.ragCitations = new ArrayList<>();
        this.maxRelevanceScore = 0.0;
        this.lowRelevanceContext = false;
    }
}
