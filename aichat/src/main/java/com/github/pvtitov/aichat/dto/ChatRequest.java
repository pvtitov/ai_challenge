package com.github.pvtitov.aichat.dto;

public class ChatRequest {
    private String prompt;
    private boolean oneShot = false;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public boolean isOneShot() {
        return oneShot;
    }

    public void setOneShot(boolean oneShot) {
        this.oneShot = oneShot;
    }
}
