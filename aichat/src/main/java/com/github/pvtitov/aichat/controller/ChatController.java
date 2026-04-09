package com.github.pvtitov.aichat.controller;

import com.github.pvtitov.aichat.dto.ChatRequest;
import com.github.pvtitov.aichat.dto.ChatResponse;
import com.github.pvtitov.aichat.dto.state.ChatState;
import com.github.pvtitov.aichat.model.WeatherLog;
import com.github.pvtitov.aichat.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Controller
@SessionAttributes("chatState")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @ModelAttribute("chatState")
    public ChatState setUpChatState() {
        return new ChatState();
    }

    @GetMapping("/")
    public String index() {
        return "index.html";
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request,
                                             @ModelAttribute("chatState") ChatState chatState) {
        try {
            ChatResponse response = chatService.process(request, chatState);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Chat processing failed for prompt: {}", request.getPrompt(), e);
            return ResponseEntity.internalServerError()
                .body(new ChatResponse("Error: " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<String> history(@ModelAttribute("chatState") ChatState chatState) {
        String history = chatService.getHistoryAsString(chatState.getCurrentProfile());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/weather-logs")
    public ResponseEntity<List<WeatherLog>> recentWeatherLogs(@ModelAttribute("chatState") ChatState chatState,
                                                              @RequestParam(defaultValue = "3") int limit) {
        String profileLogin = chatState.getCurrentProfile() != null ? chatState.getCurrentProfile().getLogin() : "";
        List<WeatherLog> logs = chatService.getRecentWeatherLogs(profileLogin, limit);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/weather-status")
    public ResponseEntity<String> weatherStatus(@ModelAttribute("chatState") ChatState chatState) {
        String profileLogin = chatState.getCurrentProfile() != null ? chatState.getCurrentProfile().getLogin() : "";
        return ResponseEntity.ok(chatService.getRecentWeatherLogs(profileLogin, 3).toString());
    }
}
