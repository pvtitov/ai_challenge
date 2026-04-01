package com.github.pvtitov.aichat.controller;

import com.github.pvtitov.aichat.dto.ChatRequest;
import com.github.pvtitov.aichat.dto.ChatResponse;
import com.github.pvtitov.aichat.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.SessionAttributes;

import java.io.IOException;

@Controller
@SessionAttributes("currentProfileLogin")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @ModelAttribute("currentProfileLogin")
    public String setUpCurrentProfileLogin() {
        return ""; // Default profile login
    }

    @GetMapping("/")
    public String index() {
        return "index.html";
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, @ModelAttribute("currentProfileLogin") String currentProfileLogin, Model model) {
        try {
            ChatResponse response = chatService.process(request.getPrompt(), currentProfileLogin, model);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
