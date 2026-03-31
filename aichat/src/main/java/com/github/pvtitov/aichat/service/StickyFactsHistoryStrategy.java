package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.model.ChatMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

public class StickyFactsHistoryStrategy implements HistoryStrategy {

    @Override
    public JSONArray getHistory(Function<Integer, List<ChatMessage>> findByBranch, int branch) {
        List<ChatMessage> messages = findByBranch.apply(branch);
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
