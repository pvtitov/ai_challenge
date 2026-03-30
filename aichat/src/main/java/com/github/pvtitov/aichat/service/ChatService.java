package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.dto.ChatResponse;

import java.io.IOException;

public interface ChatService {

    ChatResponse process(String userInput) throws IOException;
}
