package com.github.pvtitov.aichat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pvtitov.aichat.dto.ChatResponse;
import com.github.pvtitov.aichat.dto.GigaChatComplexResponse;
import com.github.pvtitov.aichat.model.ChatMessage;
import com.github.pvtitov.aichat.repository.ChatHistoryRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final GigaChatApiService gigaChatApiService;
    private final ObjectMapper objectMapper = new ObjectMapper();


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
            return new ChatResponse(commandResult, "", "", 0, 0, 0, 0);
        }

        List<ChatMessage> shortTermHistory = chatHistoryRepository.findShortTermByBranch(currentBranch);
        List<ChatMessage> midTermHistory = chatHistoryRepository.findMidTermByBranch(currentBranch);
        List<ChatMessage> longTermHistory = chatHistoryRepository.findLongTermByBranch(currentBranch);

        JSONArray combinedHistory = new JSONArray();
        longTermHistory.forEach(msg -> combinedHistory.put(new JSONObject().put("role", msg.getRole()).put("content", msg.getContent())));
        midTermHistory.forEach(msg -> combinedHistory.put(new JSONObject().put("role", msg.getRole()).put("content", msg.getContent())));
        shortTermHistory.forEach(msg -> combinedHistory.put(new JSONObject().put("role", msg.getRole()).put("content", msg.getContent())));

        GigaChatComplexResponse assistantResponse = gigaChatApiService.getCompletion(combinedHistory, userInput);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole("user");
        userMessage.setContent(userInput);
        userMessage.setBranch(currentBranch);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(assistantResponse.toString());
        assistantMessage.setBranch(currentBranch);

        updateHistory(shortTermHistory, userMessage, assistantMessage, shortTermStrategy, chatHistoryRepository::saveShortTerm, chatHistoryRepository::deleteShortTermByBranch);
        updateHistory(midTermHistory, userMessage, assistantMessage, midTermStrategy, chatHistoryRepository::saveMidTerm, chatHistoryRepository::deleteMidTermByBranch);
        updateHistory(longTermHistory, userMessage, assistantMessage, longTermStrategy, chatHistoryRepository::saveLongTerm, chatHistoryRepository::deleteLongTermByBranch);

        long cumulativeTokens = chatHistoryRepository.getCumulativeTokens(currentBranch);

        return new ChatResponse(
                assistantResponse.getFullResponse(),
                assistantResponse.getSummary(),
                assistantResponse.getStickyFacts(),
                0, 0, 0, cumulativeTokens
        );
    }

    private void updateHistory(List<ChatMessage> history, ChatMessage userMessage, ChatMessage assistantMessage, HistoryStrategy strategy, SaveMessageFunction saveFunction, DeleteBranchFunction deleteFunction) {
        List<ChatMessage> newHistory = new ArrayList<>(history);
        newHistory.add(userMessage);
        newHistory.add(assistantMessage);
        List<ChatMessage> appliedHistory = strategy.apply(newHistory);
        deleteFunction.delete(currentBranch);
        appliedHistory.forEach(saveFunction::save);
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
        shortTerm.forEach(m -> {
            m.setBranch(newBranch);
            chatHistoryRepository.saveShortTerm(m);
        });

        List<ChatMessage> midTerm = chatHistoryRepository.findMidTermByBranch(currentBranch);
        midTerm.forEach(m -> {
            m.setBranch(newBranch);
            chatHistoryRepository.saveMidTerm(m);
        });

        List<ChatMessage> longTerm = chatHistoryRepository.findLongTermByBranch(currentBranch);
        longTerm.forEach(m -> {
            m.setBranch(newBranch);
            chatHistoryRepository.saveLongTerm(m);
        });

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
        List<ChatMessage> shortTermHistory = chatHistoryRepository.findShortTermByBranch(currentBranch);
        shortTermHistory.forEach(item -> sb.append(item.getRole()).append(": ").append(item.getContent()).append("\n"));

        sb.append("\nMid Term History:\n");
        List<ChatMessage> midTermHistory = chatHistoryRepository.findMidTermByBranch(currentBranch);
        midTermHistory.forEach(item -> sb.append(item.getRole()).append(": ").append(item.getContent()).append("\n"));

        sb.append("\nLong Term History:\n");
        List<ChatMessage> longTermHistory = chatHistoryRepository.findLongTermByBranch(currentBranch);
        longTermHistory.forEach(item -> sb.append(item.getRole()).append(": ").append(item.getContent()).append("\n"));

        return sb.toString().trim();
    }

    private String setHistoryStrategy(String[] parts) {
        if (parts.length < 2) {
            return "Usage: /strategy [short|middle|long] [unlimited|sliding|sticky|summary] [size]";
        }

        String memoryType = "short"; // Default memory type
        int strategyIndex = 1;

        if (parts[1].equals("short") || parts[1].equals("middle") || parts[1].equals("long")) {
            memoryType = parts[1];
            strategyIndex = 2;
        }

        if (parts.length <= strategyIndex) {
            return "Usage: /strategy [short|middle|long] [unlimited|sliding|sticky|summary] [size]";
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
            case "summary":
                newStrategy = new SummaryHistoryStrategy();
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

    @FunctionalInterface
    interface DeleteBranchFunction {
        void delete(int branch);
    }

    
}
