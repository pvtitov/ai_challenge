package com.github.pvtitov.aichat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pvtitov.aichat.dto.ChatResponse;
import com.github.pvtitov.aichat.dto.GigaChatComplexResponse;
import com.github.pvtitov.aichat.model.ChatMessage;
import com.github.pvtitov.aichat.model.Profile;
import com.github.pvtitov.aichat.repository.ChatHistoryRepository;
import com.github.pvtitov.aichat.repository.ProfileRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final ProfileRepository profileRepository;
    private final GigaChatApiService gigaChatApiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HistoryStrategy shortTermStrategy;
    private HistoryStrategy midTermStrategy;
    private HistoryStrategy longTermStrategy;

    public ChatServiceImpl(ChatHistoryRepository chatHistoryRepository,
                           ProfileRepository profileRepository,
                           GigaChatApiService gigaChatApiService) {
        this.chatHistoryRepository = chatHistoryRepository;
        this.profileRepository = profileRepository;
        this.gigaChatApiService = gigaChatApiService;
        this.shortTermStrategy = new SlidingWindowHistoryStrategy(2);
        this.midTermStrategy = new SlidingWindowHistoryStrategy(10);
        this.longTermStrategy = new UnlimitedHistoryStrategy();
    }

    @Override
    public ChatResponse process(String userInput, String profileLogin, Model model) throws IOException {
        Optional<Profile> currentProfileOptional = profileRepository.findByLogin(profileLogin);
        Profile currentProfile = currentProfileOptional.orElseGet(() -> {
            Profile newProfile = new Profile(profileLogin, "");
            profileRepository.save(newProfile);
            return newProfile;
        });

        if (userInput.startsWith("/")) {
            String commandResult = handleCommand(userInput, currentProfile, model);
            return new ChatResponse(commandResult, "", "", 0, 0, 0, 0);
        }

        String effectiveUserInput = currentProfile.getProfileData().isEmpty() ? userInput : currentProfile.getProfileData() + "\n" + userInput;

        List<ChatMessage> shortTermHistory = chatHistoryRepository.findShortTermByBranch(currentProfile.getCurrentBranch(), profileLogin);
        List<ChatMessage> midTermHistory = chatHistoryRepository.findMidTermByBranch(currentProfile.getCurrentBranch(), profileLogin);
        List<ChatMessage> longTermHistory = chatHistoryRepository.findLongTermByBranch(currentProfile.getCurrentBranch(), profileLogin);

        JSONArray combinedHistory = new JSONArray();
        longTermHistory.forEach(msg -> combinedHistory.put(new JSONObject().put("role", msg.getRole()).put("content", msg.getContent())));
        midTermHistory.forEach(msg -> combinedHistory.put(new JSONObject().put("role", msg.getRole()).put("content", msg.getContent())));
        shortTermHistory.forEach(msg -> combinedHistory.put(new JSONObject().put("role", msg.getRole()).put("content", msg.getContent())));

        GigaChatComplexResponse assistantResponse = gigaChatApiService.getCompletion(combinedHistory, effectiveUserInput);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole("user");
        userMessage.setContent(userInput);
        userMessage.setBranch(currentProfile.getCurrentBranch());
        userMessage.setProfileLogin(profileLogin);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(assistantResponse.getFullResponse()); // Use full response for history
        assistantMessage.setBranch(currentProfile.getCurrentBranch());
        assistantMessage.setProfileLogin(profileLogin);
        assistantMessage.setPromptTokens(assistantResponse.getPromptTokens());
        assistantMessage.setCompletionTokens(assistantResponse.getCompletionTokens());
        assistantMessage.setTotalTokens(assistantResponse.getTotalTokens());

        updateHistory(shortTermHistory, userMessage, assistantMessage, shortTermStrategy, chatHistoryRepository::saveShortTerm, (branch, pLogin) -> chatHistoryRepository.deleteShortTermByBranch(branch, pLogin), currentProfile.getCurrentBranch(), profileLogin);
        updateHistory(midTermHistory, userMessage, assistantMessage, midTermStrategy, chatHistoryRepository::saveMidTerm, (branch, pLogin) -> chatHistoryRepository.deleteMidTermByBranch(branch, pLogin), currentProfile.getCurrentBranch(), profileLogin);
        updateHistory(longTermHistory, userMessage, assistantMessage, longTermStrategy, chatHistoryRepository::saveLongTerm, (branch, pLogin) -> chatHistoryRepository.deleteLongTermByBranch(branch, pLogin), currentProfile.getCurrentBranch(), profileLogin);

        long cumulativeTokens = chatHistoryRepository.getCumulativeTokens(currentProfile.getCurrentBranch(), profileLogin);

        return new ChatResponse(
                assistantResponse.getFullResponse(),
                assistantResponse.getSummary(),
                assistantResponse.getStickyFacts(),
                assistantResponse.getPromptTokens(),
                assistantResponse.getCompletionTokens(),
                assistantResponse.getTotalTokens(),
                cumulativeTokens
        );
    }

    private void updateHistory(List<ChatMessage> history, ChatMessage userMessage, ChatMessage assistantMessage, HistoryStrategy strategy, SaveMessageFunction saveFunction, DeleteBranchFunction deleteFunction, int branch, String profileLogin) {
        List<ChatMessage> newHistory = new ArrayList<>(history);
        newHistory.add(userMessage);
        newHistory.add(assistantMessage);
        List<ChatMessage> appliedHistory = strategy.apply(newHistory);
        deleteFunction.delete(branch, profileLogin);
        appliedHistory.forEach(saveFunction::save);
    }

    private String handleCommand(String command, Profile currentProfile, Model model) {
        String[] parts = command.split(" ", 3); // Limit split to 3 parts for /profile command
        String cmd = parts[0];
        String profileLogin = currentProfile.getLogin();
        int currentBranch = currentProfile.getCurrentBranch();

        switch (cmd) {
            case "/profile":
                if (parts.length < 2) {
                    return "Usage: /profile [LOGIN] [optional text to append to profile data]";
                }
                String newProfileLogin = parts[1];
                String textToAppend = parts.length > 2 ? parts[2] : "";

                Optional<Profile> targetProfileOptional = profileRepository.findByLogin(newProfileLogin);
                Profile targetProfile;

                if (targetProfileOptional.isPresent()) {
                    targetProfile = targetProfileOptional.get();
                    if (!textToAppend.isEmpty()) {
                        targetProfile.setProfileData(targetProfile.getProfileData() + "\n" + textToAppend);
                        profileRepository.update(targetProfile);
                    }
                } else {
                    targetProfile = new Profile(newProfileLogin, textToAppend);
                    profileRepository.save(targetProfile);
                }
                model.addAttribute("currentProfileLogin", newProfileLogin);
                return "Switched to profile: " + newProfileLogin + (textToAppend.isEmpty() ? "" : " and appended text.");

            case "/clean":
                chatHistoryRepository.deleteByBranch(currentBranch, profileLogin);
                return "History for branch " + currentBranch + " of profile " + profileLogin + " cleared.";
            case "/cleanAll":
                chatHistoryRepository.deleteAll(profileLogin);
                currentProfile.setCurrentBranch(1); // Reset branch to 1 after cleaning all history
                profileRepository.update(currentProfile);
                return "All history for profile " + profileLogin + " cleared and branch reset to 1.";
            case "/branch":
                return createBranch(currentProfile);
            case "/switch":
                if (parts.length > 1) {
                    return switchBranch(Integer.parseInt(parts[1]), currentProfile);
                }
                else {
                    return switchBranch(currentProfile);
                }
            case "/history":
                return getHistoryAsString(currentProfile);
            case "/strategy":
                return setHistoryStrategy(parts);
            default:
                return "Unknown command: " + cmd;
        }
    }

    private String createBranch(Profile currentProfile) {
        String profileLogin = currentProfile.getLogin();
        int oldBranch = currentProfile.getCurrentBranch();
        int newBranch = chatHistoryRepository.getMaxBranch(profileLogin) + 1;

        List<ChatMessage> shortTerm = chatHistoryRepository.findShortTermByBranch(oldBranch, profileLogin);
        shortTerm.forEach(m -> {
            m.setBranch(newBranch);
            chatHistoryRepository.saveShortTerm(m);
        });

        List<ChatMessage> midTerm = chatHistoryRepository.findMidTermByBranch(oldBranch, profileLogin);
        midTerm.forEach(m -> {
            m.setBranch(newBranch);
            chatHistoryRepository.saveMidTerm(m);
        });

        List<ChatMessage> longTerm = chatHistoryRepository.findLongTermByBranch(oldBranch, profileLogin);
        longTerm.forEach(m -> {
            m.setBranch(newBranch);
            chatHistoryRepository.saveLongTerm(m);
        });

        currentProfile.setCurrentBranch(newBranch);
        profileRepository.update(currentProfile);

        return "Switched to new branch " + newBranch + ", inheriting history from branch " + oldBranch + " for profile " + profileLogin + ".";
    }

    private String switchBranch(int branch, Profile currentProfile) {
        currentProfile.setCurrentBranch(branch);
        profileRepository.update(currentProfile);
        return "Switched to branch " + branch + " for profile " + currentProfile.getLogin() + ".";
    }

    private String switchBranch(Profile currentProfile) {
        String profileLogin = currentProfile.getLogin();
        List<Integer> branches = chatHistoryRepository.getBranches(profileLogin);
        if (branches.isEmpty()) {
            return "No branches available to switch to for profile " + profileLogin + ".";
        }
        int currentIndex = branches.indexOf(currentProfile.getCurrentBranch());
        int newBranch;
        if (currentIndex == -1 || currentIndex + 1 >= branches.size()) {
            newBranch = branches.get(0);
        } else {
            newBranch = branches.get(currentIndex + 1);
        }
        currentProfile.setCurrentBranch(newBranch);
        profileRepository.update(currentProfile);
        return "Switched to branch " + newBranch + " for profile " + profileLogin + ".";
    }

    private String getHistoryAsString(Profile currentProfile) {
        StringBuilder sb = new StringBuilder();
        String profileLogin = currentProfile.getLogin();
        int currentBranch = currentProfile.getCurrentBranch();

        sb.append("Short Term History (Profile: ").append(profileLogin).append(", Branch: ").append(currentBranch).append("):\n");
        List<ChatMessage> shortTermHistory = chatHistoryRepository.findShortTermByBranch(currentBranch, profileLogin);
        shortTermHistory.forEach(item -> sb.append(item.getRole()).append(": ").append(item.getContent()).append("\n"));

        sb.append("\nMid Term History (Profile: ").append(profileLogin).append(", Branch: ").append(currentBranch).append("):\n");
        List<ChatMessage> midTermHistory = chatHistoryRepository.findMidTermByBranch(currentBranch, profileLogin);
        midTermHistory.forEach(item -> sb.append(item.getRole()).append(": ").append(item.getContent()).append("\n"));

        sb.append("\nLong Term History (Profile: ").append(profileLogin).append(", Branch: ").append(currentBranch).append("):\n");
        List<ChatMessage> longTermHistory = chatHistoryRepository.findLongTermByBranch(currentBranch, profileLogin);
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
        void delete(int branch, String profileLogin);
    }
}
