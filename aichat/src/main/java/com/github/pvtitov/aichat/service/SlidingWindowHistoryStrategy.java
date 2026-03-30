package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.model.ChatMessage;
import com.github.pvtitov.aichat.repository.ChatHistoryRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("slidingWindowHistoryStrategy")
public class SlidingWindowHistoryStrategy implements HistoryStrategy {

    private int windowSize = 5;

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    @Override
    public JSONArray getHistory(ChatHistoryRepository repository, int branch) {
        List<ChatMessage> messages = repository.findByBranch(branch);
        JSONArray jsonArray = new JSONArray();
        int startIndex = Math.max(0, messages.size() - windowSize * 2);
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
