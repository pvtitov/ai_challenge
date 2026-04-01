package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.dto.ChatResponse;
import org.springframework.ui.Model;

import java.io.IOException;

public interface ChatService {
    ChatResponse process(String userInput, String profileLogin, Model model) throws IOException;
}

