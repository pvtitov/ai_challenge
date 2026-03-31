package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.model.ChatMessage;

import java.util.List;

public class SlidingWindowHistoryStrategy implements HistoryStrategy {

    private int windowSize;

    public SlidingWindowHistoryStrategy() {
        this.windowSize = 5;
    }

    public SlidingWindowHistoryStrategy(int windowSize) {
        this.windowSize = windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    @Override
    public List<ChatMessage> apply(List<ChatMessage> messages) {
        if (messages.size() <= windowSize) {
            return messages;
        }
        return messages.subList(messages.size() - windowSize, messages.size());
    }
}
