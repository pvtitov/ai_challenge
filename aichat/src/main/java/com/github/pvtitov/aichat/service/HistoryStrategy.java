package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.model.ChatMessage;

import java.util.List;

@FunctionalInterface
public interface HistoryStrategy {
    List<ChatMessage> apply(List<ChatMessage> messages);
}
