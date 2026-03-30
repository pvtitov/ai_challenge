package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.dto.ChatResponse;
import com.github.pvtitov.aichat.model.ChatMessage;
import com.github.pvtitov.aichat.repository.ChatHistoryRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final GigaChatApiService gigaChatApiService;
    private final HistoryStrategy unlimitedHistoryStrategy;
    private final HistoryStrategy slidingWindowHistoryStrategy;
    private final HistoryStrategy stickyFactsHistoryStrategy;

    private HistoryStrategy currentHistoryStrategy;
    private int currentBranch = 1;

    public ChatServiceImpl(ChatHistoryRepository chatHistoryRepository,
                           GigaChatApiService gigaChatApiService,
                           HistoryStrategy unlimitedHistoryStrategy,
                           HistoryStrategy slidingWindowHistoryStrategy,
                           HistoryStrategy stickyFactsHistoryStrategy) {
        this.chatHistoryRepository = chatHistoryRepository;
        this.gigaChatApiService = gigaChatApiService;
        this.unlimitedHistoryStrategy = unlimitedHistoryStrategy;
        this.slidingWindowHistoryStrategy = slidingWindowHistoryStrategy;
        this.stickyFactsHistoryStrategy = stickyFactsHistoryStrategy;
        this.currentHistoryStrategy = unlimitedHistoryStrategy;
    }

    @Override
    public ChatResponse process(String userInput) throws IOException {
        if (userInput.startsWith("/")) {
            String commandResult = handleCommand(userInput);
            return new ChatResponse(commandResult, 0, 0, 0, 0);
        }

        saveMessage("user", userInput, currentBranch, null, null, null);

        JSONArray history = currentHistoryStrategy.getHistory(chatHistoryRepository, currentBranch);
        String assistantMessage = gigaChatApiService.getCompletion(history);

        saveMessage("assistant", assistantMessage, currentBranch, null, null, null);

        long cumulativeTokens = chatHistoryRepository.getCumulativeTokens(currentBranch);

        return new ChatResponse(assistantMessage, 0, 0, 0, cumulativeTokens);
    }

    private String handleCommand(String command) {
        String[] parts = command.split(" ");
        String cmd = parts[0];

        switch (cmd) {
            case "/clean":
                chatHistoryRepository.deleteByBranch(currentBranch);
                return "History for branch " + currentBranch + " cleared.";
            case "/branch":
                return createBranch();
            case "/switch":
                if (parts.length > 1) {
                    return switchBranch(Integer.parseInt(parts[1]));
                } else {
                    return switchBranch();
                }
            case "/history":
                return getHistoryAsString();
            case "/strategy":
                if (parts.length > 1) {
                    return setHistoryStrategy(parts[1], parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
                } else {
                    return "Usage: /strategy [unlimited|sliding|sticky] [size]";
                }
            default:
                return "Unknown command: " + cmd;
        }
    }

    private String createBranch() {
        int newBranch = chatHistoryRepository.getMaxBranch() + 1;
        JSONArray history = currentHistoryStrategy.getHistory(chatHistoryRepository, currentBranch);
        for (int i = 0; i < history.length(); i++) {
            JSONObject message = history.getJSONObject(i);
            saveMessage(message.getString("role"), message.getString("content"), newBranch, null, null, null);
        }
        int previousBranch = currentBranch;
        currentBranch = newBranch;
        return "Switched to new branch " + currentBranch + ", inheriting history from branch " + previousBranch + " based on the current strategy.";
    }

    private String switchBranch(int branch) {
        this.currentBranch = branch;
        return "Switched to branch " + branch;
    }

    private String switchBranch() {
        List<Integer> branches = chatHistoryRepository.getBranches();
        if (branches.isEmpty()) {
            return "No branches available to switch to.";
        }
        int currentIndex = branches.indexOf(currentBranch);
        if (currentIndex == -1 || currentIndex + 1 >= branches.size()) {
            currentBranch = branches.get(0);
        } else {
            currentBranch = branches.get(currentIndex + 1);
        }
        return "Switched to branch " + currentBranch;
    }

    private String getHistoryAsString() {
        StringBuilder sb = new StringBuilder();
        List<ChatMessage> messages = chatHistoryRepository.findByBranch(currentBranch);
        for (ChatMessage message : messages) {
            sb.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
        }
        return sb.toString();
    }

    private String setHistoryStrategy(String strategy, int size) {
        switch (strategy) {
            case "unlimited":
                this.currentHistoryStrategy = unlimitedHistoryStrategy;
                return "History strategy set to unlimited.";
            case "sliding":
                if (size > 0) {
                    ((SlidingWindowHistoryStrategy) slidingWindowHistoryStrategy).setWindowSize(size);
                }
                this.currentHistoryStrategy = slidingWindowHistoryStrategy;
                return "History strategy set to sliding window with size " + size + ".";
            case "sticky":
                this.currentHistoryStrategy = stickyFactsHistoryStrategy;
                return "History strategy set to sticky facts.";
            default:
                return "Unknown strategy: " + strategy;
        }
    }

    private void saveMessage(String role, String content, int branch, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        ChatMessage message = new ChatMessage();
        message.setBranch(branch);
        message.setRole(role);
        message.setContent(content);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setTotalTokens(totalTokens);
        chatHistoryRepository.save(message);
    }
}
