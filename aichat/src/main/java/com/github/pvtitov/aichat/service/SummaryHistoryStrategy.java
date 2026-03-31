package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.model.ChatMessage;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SummaryHistoryStrategy implements HistoryStrategy {
    @Override
    public List<ChatMessage> apply(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        String summary = messages.stream()
                .map(ChatMessage::getContent)
                .collect(Collectors.joining(" "));

        ChatMessage summaryMessage = new ChatMessage();
        summaryMessage.setRole("system");
        summaryMessage.setContent("Summary of previous conversation: " + summary);

        return Collections.singletonList(summaryMessage);
    }
}
