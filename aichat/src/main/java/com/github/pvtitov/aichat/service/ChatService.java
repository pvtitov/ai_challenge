package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.dto.ChatRequest;
import com.github.pvtitov.aichat.dto.ChatResponse;
import com.github.pvtitov.aichat.dto.state.ChatState;
import com.github.pvtitov.aichat.model.Profile;
import com.github.pvtitov.aichat.model.WeatherLog;

import java.io.IOException;
import java.time.Duration;
import java.util.List; // Added import

public interface ChatService {
    ChatResponse process(ChatRequest request, ChatState chatState) throws IOException;
    String getHistoryAsString(Profile profile);
    List<String> listMcpServers();
    String startWeatherScheduler(String profileLogin, String city, Duration period, int summaryCount);
    String stopWeatherScheduler(String profileLogin);
    List<WeatherLog> getRecentWeatherLogs(String profileLogin, int limit);
}

