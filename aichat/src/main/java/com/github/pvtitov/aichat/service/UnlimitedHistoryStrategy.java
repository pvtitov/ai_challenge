package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.model.ChatMessage;

import java.util.List;

public class UnlimitedHistoryStrategy implements HistoryStrategy {

    @Override
    public List<ChatMessage> apply(List<ChatMessage> messages) {
        return messages;
    }
}
