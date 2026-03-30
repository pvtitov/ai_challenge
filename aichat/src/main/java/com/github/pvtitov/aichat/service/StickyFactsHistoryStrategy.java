package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.model.ChatMessage;
import com.github.pvtitov.aichat.repository.ChatHistoryRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("stickyFactsHistoryStrategy")
public class StickyFactsHistoryStrategy implements HistoryStrategy {

    @Override
    public JSONArray getHistory(ChatHistoryRepository repository, int branch) {
        List<ChatMessage> messages = repository.findByBranch(branch);
        JSONArray jsonArray = new JSONArray();
        if (!messages.isEmpty()) {
            ChatMessage lastMessage = messages.get(messages.size() - 1);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("role", "system");
            jsonObject.put("content", "Remember this fact: " + lastMessage.getContent());
            jsonArray.put(jsonObject);
        }
        return jsonArray;
    }
}
