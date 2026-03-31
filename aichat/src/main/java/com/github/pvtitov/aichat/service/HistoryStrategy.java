package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.model.ChatMessage;
import org.json.JSONArray;

import java.util.List;
import java.util.function.Function;

@FunctionalInterface
public interface HistoryStrategy {
    JSONArray getHistory(Function<Integer, List<ChatMessage>> findByBranch, int branch);
}
