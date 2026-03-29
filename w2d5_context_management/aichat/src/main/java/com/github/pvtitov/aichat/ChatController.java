package com.github.pvtitov.aichat;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class ChatController {

    private final AichatManager aichatManager;

    public ChatController(AichatManager aichatManager) {
        this.aichatManager = aichatManager;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) throws IOException {
        return aichatManager.process(request.getPrompt());
    }
}
