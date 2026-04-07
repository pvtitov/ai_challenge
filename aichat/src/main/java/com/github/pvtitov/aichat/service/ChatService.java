package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.dto.ChatRequest;
import com.github.pvtitov.aichat.dto.ChatResponse;
import com.github.pvtitov.aichat.dto.state.ChatState;
import com.github.pvtitov.aichat.model.Profile;

import java.io.IOException;
import java.util.List; // Added import

public interface ChatService {
    ChatResponse process(ChatRequest request, ChatState chatState) throws IOException;
    String getHistoryAsString(Profile profile);
    List<String> listMcpServers();
}

