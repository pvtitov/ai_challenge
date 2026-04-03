package com.github.pvtitov.aichat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pvtitov.aichat.dto.*;
import com.github.pvtitov.aichat.dto.state.ChatState;
import com.github.pvtitov.aichat.dto.state.ConversationState;
import com.github.pvtitov.aichat.dto.state.Stage;
import com.github.pvtitov.aichat.model.ChatMessage;
import com.github.pvtitov.aichat.model.ChatMessage;
import com.github.pvtitov.aichat.model.Invariant; // Added
import com.github.pvtitov.aichat.model.Profile;
import com.github.pvtitov.aichat.repository.ChatHistoryRepository;
import com.github.pvtitov.aichat.repository.InvariantRepository; // Added
import com.github.pvtitov.aichat.repository.ProfileRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final ProfileRepository profileRepository;
    private final InvariantRepository invariantRepository; // New dependency
    private final GigaChatApiService gigaChatApiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HistoryStrategy shortTermStrategy;
    private HistoryStrategy midTermStrategy;
    private HistoryStrategy longTermStrategy;

    public ChatServiceImpl(ChatHistoryRepository chatHistoryRepository,
                           ProfileRepository profileRepository,
                           InvariantRepository invariantRepository, // New dependency
                           GigaChatApiService gigaChatApiService) {
        this.chatHistoryRepository = chatHistoryRepository;
        this.profileRepository = profileRepository;
        this.invariantRepository = invariantRepository; // Initialize new dependency
        this.gigaChatApiService = gigaChatApiService;
        this.shortTermStrategy = new SlidingWindowHistoryStrategy(2);
        this.midTermStrategy = new SlidingWindowHistoryStrategy(10);
        this.longTermStrategy = new UnlimitedHistoryStrategy();
    }

    @Override
    public ChatResponse process(ChatRequest request, ChatState chatState) throws IOException {
        String userInput = request.getPrompt();
        ConversationState conversationState = chatState.getConversationState();

        // Ensure profile is handled correctly
        Profile currentProfile = chatState.getCurrentProfile();
        if (currentProfile == null) {
            // Default to an empty profile login if none is set
            String profileLogin = "";
            Optional<Profile> profileOpt = profileRepository.findByLogin(profileLogin);
            currentProfile = profileOpt.orElseGet(() -> {
                Profile newProfile = new Profile(profileLogin, "");
                profileRepository.save(newProfile);
                return newProfile;
            });
            chatState.setCurrentProfile(currentProfile);
        }

        if (userInput.startsWith("/")) {
            return new ChatResponse(handleCommand(userInput, chatState));
        }

        switch (conversationState.getStage()) {
            case AWAITING_PLAN_APPROVAL:
                return handlePlanApproval(userInput, conversationState, currentProfile);
            case AWAITING_ACTION_APPROVAL:
                return handleActionApproval(userInput, conversationState, currentProfile);
            case AWAITING_PROMPT:
            default:
                return handleAwaitingPrompt(userInput, conversationState, currentProfile);
        }
    }

    private ChatResponse handleAwaitingPrompt(String userInput, ConversationState conversationState, Profile currentProfile) throws IOException {
        conversationState.setOriginalPrompt(userInput);

        String invariantPrefix = getInvariantsAsPromptPrefix(currentProfile);
        String planPrompt = invariantPrefix + "You are a planning AI. Based on the user's request, create a step-by-step plan. The user's request is: \"" + userInput + "\". Respond with the plan only.";
        GigaChatComplexResponse planResponse = gigaChatApiService.getCompletion(new JSONArray(), planPrompt);
        String plan = planResponse.getFullResponse();
        conversationState.setLastPlan(plan);
        conversationState.setStage(Stage.AWAITING_PLAN_APPROVAL);

        ChatResponse response = new ChatResponse(plan);
        response.setResponseType(ResponseType.PLAN);
        response.setRequiresConfirmation(true);
        return response;
    }

    private ChatResponse handlePlanApproval(String confirmation, ConversationState conversationState, Profile currentProfile) throws IOException {
        if (!"y".equalsIgnoreCase(confirmation)) {
            conversationState.reset();
            return new ChatResponse("Plan rejected. Please provide a new prompt.");
        }

        String plan = conversationState.getLastPlan();
        String invariantPrefix = getInvariantsAsPromptPrefix(currentProfile); // Get invariants
        String actionPrompt = invariantPrefix + "You are an executing AI. Carry out the following plan: \"" + plan + "\". The original user request was: \"" + conversationState.getOriginalPrompt() + "\".";
        
        // Include history in the action prompt
        JSONArray history = getHistoryAsJson(currentProfile);
        GigaChatComplexResponse actionResponse = gigaChatApiService.getCompletion(history, actionPrompt);
        String actionResult = actionResponse.getFullResponse();

        conversationState.setStage(Stage.AWAITING_ACTION_APPROVAL);

        ChatResponse response = new ChatResponse(actionResult);
        response.setResponseType(ResponseType.ACTION_RESULT);
        response.setRequiresConfirmation(true);
        return response;
    }

    private ChatResponse handleActionApproval(String confirmation, ConversationState conversationState, Profile currentProfile) throws IOException {
        if (!"y".equalsIgnoreCase(confirmation)) {
            conversationState.reset();
            return new ChatResponse("Action aborted. Please provide a new prompt.");
        }

        String invariantPrefix = getInvariantsAsPromptPrefix(currentProfile); // Get invariants
        String verificationPrompt = invariantPrefix + "You are a verification AI. Please review the original request, the plan, and the action's result. Verify if the task is complete and correct. Original request: \"" + conversationState.getOriginalPrompt() + "\". Plan: \"" + conversationState.getLastPlan() + "\".";
        
        // Include history in the verification prompt
        JSONArray history = getHistoryAsJson(currentProfile);
        GigaChatComplexResponse verificationResponse = gigaChatApiService.getCompletion(history, verificationPrompt);

        String verificationResult = verificationResponse.getFullResponse();

        // Check if verification failed (assuming LLM indicates failure explicitly)
        if (verificationResult.startsWith("VERIFICATION_FAILED")) {
            // Do not save to history yet, as action needs to be re-run
            // Keep the stage as AWAITING_ACTION_APPROVAL to allow re-running the action
            // conversationState.setStage(Stage.AWAITING_ACTION_APPROVAL); // Already in this stage

            ChatResponse response = new ChatResponse(
                "Verification failed: " + verificationResult + "\nDo you want to re-run the action stage? (y/n)",
                verificationResponse.getSummary(),
                verificationResponse.getStickyFacts(),
                verificationResponse.getPromptTokens(),
                verificationResponse.getCompletionTokens(),
                verificationResponse.getTotalTokens(),
                chatHistoryRepository.getCumulativeTokens(currentProfile.getCurrentBranch(), currentProfile.getLogin()) // Get cumulative tokens even if not saving
            );
            response.setResponseType(ResponseType.INFO); // Indicate it's an info message
            response.setRequiresConfirmation(true); // Ask for confirmation to re-run
            return response;
        } else {
            // Verification successful
            // Save messages to history
            saveConversationToHistory(conversationState.getOriginalPrompt(), verificationResult, currentProfile);
            
            long cumulativeTokens = chatHistoryRepository.getCumulativeTokens(currentProfile.getCurrentBranch(), currentProfile.getLogin());
            
            // Reset for the next interaction
            conversationState.reset();

            return new ChatResponse(
                verificationResult,
                verificationResponse.getSummary(),
                verificationResponse.getStickyFacts(),
                verificationResponse.getPromptTokens(),
                verificationResponse.getCompletionTokens(),
                verificationResponse.getTotalTokens(),
                cumulativeTokens
            );
        }
    }

    private void saveConversationToHistory(String userInput, String assistantResponse, Profile profile) {
        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole("user");
        userMessage.setContent(userInput);
        userMessage.setBranch(profile.getCurrentBranch());
        userMessage.setProfileLogin(profile.getLogin());

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(assistantResponse);
        assistantMessage.setBranch(profile.getCurrentBranch());
        assistantMessage.setProfileLogin(profile.getLogin());

        List<ChatMessage> shortTermHistory = chatHistoryRepository.findShortTermByBranch(profile.getCurrentBranch(), profile.getLogin());
        updateHistory(shortTermHistory, userMessage, assistantMessage, shortTermStrategy, chatHistoryRepository::saveShortTerm, (branch, pLogin) -> chatHistoryRepository.deleteShortTermByBranch(branch, pLogin), profile.getCurrentBranch(), profile.getLogin());
        
        List<ChatMessage> midTermHistory = chatHistoryRepository.findMidTermByBranch(profile.getCurrentBranch(), profile.getLogin());
        updateHistory(midTermHistory, userMessage, assistantMessage, midTermStrategy, chatHistoryRepository::saveMidTerm, (branch, pLogin) -> chatHistoryRepository.deleteMidTermByBranch(branch, pLogin), profile.getCurrentBranch(), profile.getLogin());

        List<ChatMessage> longTermHistory = chatHistoryRepository.findLongTermByBranch(profile.getCurrentBranch(), profile.getLogin());
        updateHistory(longTermHistory, userMessage, assistantMessage, longTermStrategy, chatHistoryRepository::saveLongTerm, (branch, pLogin) -> chatHistoryRepository.deleteLongTermByBranch(branch, pLogin), profile.getCurrentBranch(), profile.getLogin());
    }
    
    private JSONArray getHistoryAsJson(Profile currentProfile) {
        List<ChatMessage> shortTermHistory = chatHistoryRepository.findShortTermByBranch(currentProfile.getCurrentBranch(), currentProfile.getLogin());
        List<ChatMessage> midTermHistory = chatHistoryRepository.findMidTermByBranch(currentProfile.getCurrentBranch(), currentProfile.getLogin());
        List<ChatMessage> longTermHistory = chatHistoryRepository.findLongTermByBranch(currentProfile.getCurrentBranch(), currentProfile.getLogin());

        JSONArray combinedHistory = new JSONArray();
        longTermHistory.forEach(msg -> combinedHistory.put(new JSONObject().put("role", msg.getRole()).put("content", msg.getContent())));
        midTermHistory.forEach(msg -> combinedHistory.put(new JSONObject().put("role", msg.getRole()).put("content", msg.getContent())));
        shortTermHistory.forEach(msg -> combinedHistory.put(new JSONObject().put("role", msg.getRole()).put("content", msg.getContent())));
        return combinedHistory;
    }

    private void updateHistory(List<ChatMessage> history, ChatMessage userMessage, ChatMessage assistantMessage, HistoryStrategy strategy, SaveMessageFunction saveFunction, DeleteBranchFunction deleteFunction, int branch, String profileLogin) {
        List<ChatMessage> newHistory = new ArrayList<>(history);
        newHistory.add(userMessage);
        newHistory.add(assistantMessage);
        List<ChatMessage> appliedHistory = strategy.apply(newHistory);
        deleteFunction.delete(branch, profileLogin);
        appliedHistory.forEach(saveFunction::save);
    }

    private String handleCommand(String command, ChatState chatState) {
        String[] parts = command.split(" ", 3);
        String cmd = parts[0];
        Profile currentProfile = chatState.getCurrentProfile();
        String profileLogin = currentProfile.getLogin();
        int currentBranch = currentProfile.getCurrentBranch();

        switch (cmd) {
            case "/invariant":
                // Find the index of the first space after "/invariant"
                int invariantStartIndex = command.indexOf(" ");
                if (invariantStartIndex == -1 || invariantStartIndex + 1 >= command.length()) {
                    return "Usage: /invariant [TEXT of invariant]";
                }
                // Extract the text after "/invariant "
                String invariantText = command.substring(invariantStartIndex + 1).trim();
                if (invariantText.isEmpty()) {
                    return "Usage: /invariant [TEXT of invariant]";
                }
                Invariant invariant = new Invariant(invariantText, currentBranch, profileLogin);
                invariantRepository.save(invariant);
                return "Invariant added: \"" + invariantText + "\"";
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
                chatState.setCurrentProfile(targetProfile);
                return "Switched to profile: " + newProfileLogin + (textToAppend.isEmpty() ? "" : " and appended text.");

            case "/clean":
                chatHistoryRepository.deleteByBranch(currentBranch, profileLogin);
                invariantRepository.deleteByBranch(currentBranch, profileLogin);
                return "History and invariants for branch " + currentBranch + " of profile " + profileLogin + " cleared.";
            case "/cleanAll":
                chatHistoryRepository.deleteAll(profileLogin);
                invariantRepository.deleteAll(profileLogin);
                currentProfile.setCurrentBranch(1);
                profileRepository.update(currentProfile);
                return "All history and invariants for profile " + profileLogin + " cleared and branch reset to 1.";
            case "/branch":
                return createBranch(currentProfile);
            case "/switch":
                if (parts.length > 1) {
                    return switchBranch(Integer.parseInt(parts[1]), currentProfile);
                } else {
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

    @Override
    public String getHistoryAsString(Profile currentProfile) {
        StringBuilder sb = new StringBuilder();
        String profileLogin = currentProfile.getLogin();
        int currentBranch = currentProfile.getCurrentBranch();

        sb.append("Invariants (Profile: ").append(profileLogin).append(", Branch: ").append(currentBranch).append("):\n");
        List<Invariant> invariants = invariantRepository.findByBranch(currentBranch, profileLogin);
        if (invariants.isEmpty()) {
            sb.append("None\n");
        } else {
            invariants.forEach(item -> sb.append("- ").append(item.getText()).append("\n"));
        }

        sb.append("\nShort Term History (Profile: ").append(profileLogin).append(", Branch: ").append(currentBranch).append("):\n");
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

        String memoryType = "short";
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

    private String getInvariantsAsPromptPrefix(Profile currentProfile) {
        List<Invariant> invariants = invariantRepository.findByBranch(currentProfile.getCurrentBranch(), currentProfile.getLogin());
        if (invariants.isEmpty()) {
            return "";
        }
        StringBuilder prefix = new StringBuilder("The following are invariants that must always be true:\n");
        for (int i = 0; i < invariants.size(); i++) {
            prefix.append(i + 1).append(". ").append(invariants.get(i).getText()).append("\n");
        }
        return prefix.toString();
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
