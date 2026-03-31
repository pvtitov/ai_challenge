package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.dto.ChatResponse;
import com.github.pvtitov.aichat.dto.GigaChatComplexResponse;
import com.github.pvtitov.aichat.model.ChatMessage;
import com.github.pvtitov.aichat.repository.ChatHistoryRepository;
import org.json.JSONArray;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final GigaChatApiService gigaChatApiService;

    private HistoryStrategy shortTermStrategy;
    private HistoryStrategy midTermStrategy;
    private HistoryStrategy longTermStrategy;

    private int currentBranch = 1;

    public ChatServiceImpl(ChatHistoryRepository chatHistoryRepository,
                           GigaChatApiService gigaChatApiService) {
        this.chatHistoryRepository = chatHistoryRepository;
        this.gigaChatApiService = gigaChatApiService;
        this.shortTermStrategy = new SlidingWindowHistoryStrategy(2);
        this.midTermStrategy = new SlidingWindowHistoryStrategy(10);
        this.longTermStrategy = new UnlimitedHistoryStrategy();
    }

    @Override
    public ChatResponse process(String userInput) throws IOException {
        if (userInput.startsWith("/")) {
            String commandResult = handleCommand(userInput);
            return new ChatResponse(commandResult, 0, 0, 0, 0);
        }

        saveMessage(chatHistoryRepository::saveShortTerm, "user", userInput, currentBranch, null, null, null);

        JSONArray shortTermHistory = shortTermStrategy.getHistory(chatHistoryRepository::findShortTermByBranch, currentBranch);
        JSONArray midTermHistory = midTermStrategy.getHistory(chatHistoryRepository::findMidTermByBranch, currentBranch);
        JSONArray longTermHistory = longTermStrategy.getHistory(chatHistoryRepository::findLongTermByBranch, currentBranch);

        JSONArray combinedHistory = new JSONArray();
        shortTermHistory.forEach(combinedHistory::put);
        midTermHistory.forEach(combinedHistory::put);
        longTermHistory.forEach(combinedHistory::put);

        GigaChatComplexResponse assistantResponse = gigaChatApiService.getCompletion(combinedHistory, userInput);

        saveMessage(chatHistoryRepository::saveShortTerm, "assistant", assistantResponse.getFullResponse(), currentBranch, null, null, null);
        saveMessage(chatHistoryRepository::saveMidTerm, "assistant", assistantResponse.getSummary(), currentBranch, null, null, null);
        saveMessage(chatHistoryRepository::saveLongTerm, "assistant", assistantResponse.getStickyFacts(), currentBranch, null, null, null);

        long cumulativeTokens = chatHistoryRepository.getCumulativeTokens(currentBranch);

        return new ChatResponse(assistantResponse.getFullResponse(), 0, 0, 0, cumulativeTokens);
    }

    private String handleCommand(String command) {
        String[] parts = command.split(" ");
        String cmd = parts[0];

        switch (cmd) {
            case "/clean":
                chatHistoryRepository.deleteByBranch(currentBranch);
                return "History for branch " + currentBranch + " cleared.";
            case "/cleanAll":
                chatHistoryRepository.deleteAll();
                return "All history cleared.";
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
                return setHistoryStrategy(parts);
            default:
                return "Unknown command: " + cmd;
        }
    }

    private String createBranch() {
        int newBranch = chatHistoryRepository.getMaxBranch() + 1;

        List<ChatMessage> shortTerm = chatHistoryRepository.findShortTermByBranch(currentBranch);
        shortTerm.forEach(m -> saveMessage(chatHistoryRepository::saveShortTerm, m.getRole(), m.getContent(), newBranch, m.getPromptTokens(), m.getCompletionTokens(), m.getTotalTokens()));

        List<ChatMessage> midTerm = chatHistoryRepository.findMidTermByBranch(currentBranch);
        midTerm.forEach(m -> saveMessage(chatHistoryRepository::saveMidTerm, m.getRole(), m.getContent(), newBranch, m.getPromptTokens(), m.getCompletionTokens(), m.getTotalTokens()));

        List<ChatMessage> longTerm = chatHistoryRepository.findLongTermByBranch(currentBranch);
        longTerm.forEach(m -> saveMessage(chatHistoryRepository::saveLongTerm, m.getRole(), m.getContent(), newBranch, m.getPromptTokens(), m.getCompletionTokens(), m.getTotalTokens()));

        int previousBranch = currentBranch;
        currentBranch = newBranch;
        return "Switched to new branch " + currentBranch + ", inheriting history from branch " + previousBranch + ".";
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

        sb.append("Short Term History:\n");
        JSONArray shortTermHistory = shortTermStrategy.getHistory(chatHistoryRepository::findShortTermByBranch, currentBranch);
        shortTermHistory.forEach(item -> {
            org.json.JSONObject obj = (org.json.JSONObject) item;
            sb.append(obj.getString("role")).append(": ").append(obj.getString("content")).append("\n");
        });

        sb.append("\nMid Term History:\n");
        JSONArray midTermHistory = midTermStrategy.getHistory(chatHistoryRepository::findMidTermByBranch, currentBranch);
        midTermHistory.forEach(item -> {
            org.json.JSONObject obj = (org.json.JSONObject) item;
            sb.append(obj.getString("role")).append(": ").append(obj.getString("content")).append("\n");
        });

        sb.append("\nLong Term History:\n");
        JSONArray longTermHistory = longTermStrategy.getHistory(chatHistoryRepository::findLongTermByBranch, currentBranch);
        longTermHistory.forEach(item -> {
            org.json.JSONObject obj = (org.json.JSONObject) item;
            sb.append(obj.getString("role")).append(": ").append(obj.getString("content")).append("\n");
        });

        return sb.toString().trim();
    }

    private String setHistoryStrategy(String[] parts) {
        if (parts.length < 2) {
            return "Usage: /strategy [short|middle|long] [unlimited|sliding|sticky] [size]";
        }

        String memoryType = "short"; // Default memory type
        int strategyIndex = 1;

        if (parts[1].equals("short") || parts[1].equals("middle") || parts[1].equals("long")) {
            memoryType = parts[1];
            strategyIndex = 2;
        }

        if (parts.length <= strategyIndex) {
            return "Usage: /strategy [short|middle|long] [unlimited|sliding|sticky] [size]";
        }

        String strategyType = parts[strategyIndex];
        int size = 0;
        if (parts.length > strategyIndex + 1) {
            try {
                size = Integer.parseInt(parts[strategyIndex + 1]);
            } catch (NumberFormatException e) {
                return "Invalid size: " + parts[strategyIndex + 1];
            }
        }

        HistoryStrategy newStrategy;
        switch (strategyType) {
            case "unlimited":
                newStrategy = new UnlimitedHistoryStrategy();
                break;
            case "sliding":
                if (size <= 0) return "Sliding window strategy requires a size greater than 0.";
                newStrategy = new SlidingWindowHistoryStrategy(size);
                break;
            case "sticky":
                newStrategy = new StickyFactsHistoryStrategy();
                break;
            default:
                return "Unknown strategy: " + strategyType;
        }

        switch (memoryType) {
            case "short":
                shortTermStrategy = newStrategy;
                break;
            case "middle":
                midTermStrategy = newStrategy;
                break;
            case "long":
                longTermStrategy = newStrategy;
                break;
        }

        return String.format("%s term memory strategy set to %s%s",
                memoryType.substring(0, 1).toUpperCase() + memoryType.substring(1),
                strategyType,
                (size > 0 ? " with size " + size : ""));
    }

    @FunctionalInterface
    interface SaveMessageFunction {
        void save(ChatMessage message);
    }

    private void saveMessage(SaveMessageFunction saveFunction, String role, String content, int branch, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        ChatMessage message = new ChatMessage();
        message.setBranch(branch);
        message.setRole(role);
        message.setContent(content);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setTotalTokens(totalTokens);
        saveFunction.save(message);
    }
}
