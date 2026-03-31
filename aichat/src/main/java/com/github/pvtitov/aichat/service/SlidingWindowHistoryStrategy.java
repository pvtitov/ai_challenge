package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.model.ChatMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

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
    public JSONArray getHistory(Function<Integer, List<ChatMessage>> findByBranch, int branch) {
        List<ChatMessage> messages = findByBranch.apply(branch);
        JSONArray jsonArray = new JSONArray();
        int startIndex = Math.max(0, messages.size() - windowSize);
        for (int i = startIndex; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("role", message.getRole());
            jsonObject.put("content", message.getContent());
            jsonArray.put(jsonObject);
        }
        return jsonArray;
    }
}
