package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.model.ChatMessage;
import com.github.pvtitov.aichat.repository.ChatHistoryRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("unlimitedHistoryStrategy")
public class UnlimitedHistoryStrategy implements HistoryStrategy {

    @Override
    public JSONArray getHistory(ChatHistoryRepository repository, int branch) {
        List<ChatMessage> messages = repository.findByBranch(branch);
        JSONArray jsonArray = new JSONArray();
        for (ChatMessage message : messages) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("role", message.getRole());
            jsonObject.put("content", message.getContent());
            jsonArray.put(jsonObject);
        }
        return jsonArray;
    }
}
