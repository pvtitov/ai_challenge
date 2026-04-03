package com.github.pvtitov.aichat.controller;

import com.github.pvtitov.aichat.dto.ChatRequest;
import com.github.pvtitov.aichat.dto.ChatResponse;
import com.github.pvtitov.aichat.dto.state.ChatState;
import com.github.pvtitov.aichat.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Controller
@SessionAttributes("chatState")
public class ChatController {

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
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/history")
    public ResponseEntity<String> history(@ModelAttribute("chatState") ChatState chatState) {
        String history = chatService.getHistoryAsString(chatState.getCurrentProfile());
        return ResponseEntity.ok(history);
    }
}
