package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.repository.ChatHistoryRepository;
import org.json.JSONArray;

public interface HistoryStrategy {
    JSONArray getHistory(ChatHistoryRepository repository, int branch);
}
