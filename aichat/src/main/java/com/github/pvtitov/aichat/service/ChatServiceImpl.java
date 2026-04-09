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
import com.github.pvtitov.aichat.model.WeatherLog;
import com.github.pvtitov.aichat.repository.ChatHistoryRepository;
import com.github.pvtitov.aichat.repository.InvariantRepository; // Added
import com.github.pvtitov.aichat.repository.ProfileRepository;
import com.github.pvtitov.aichat.repository.WeatherLogRepository;
import com.github.pvtitov.aichat.service.WeatherSchedulerService.WeatherSchedulerConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final ProfileRepository profileRepository;
    private final InvariantRepository invariantRepository; // New dependency
    private final WeatherLogRepository weatherLogRepository;
    private final GigaChatApiService gigaChatApiService;
    private final McpService mcpService; // New dependency
    private final WeatherSchedulerService weatherSchedulerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HistoryStrategy shortTermStrategy;
    private HistoryStrategy midTermStrategy;
    private HistoryStrategy longTermStrategy;

    public ChatServiceImpl(ChatHistoryRepository chatHistoryRepository,
                           ProfileRepository profileRepository,
                           InvariantRepository invariantRepository,
                           WeatherLogRepository weatherLogRepository,
                           GigaChatApiService gigaChatApiService,
                           McpService mcpService,
                           WeatherSchedulerService weatherSchedulerService) { // New dependency
        this.chatHistoryRepository = chatHistoryRepository;
        this.profileRepository = profileRepository;
        this.invariantRepository = invariantRepository;
        this.weatherLogRepository = weatherLogRepository;
        this.gigaChatApiService = gigaChatApiService;
        this.mcpService = mcpService; // Initialize new dependency
        this.weatherSchedulerService = weatherSchedulerService;
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

        // Check if we should call an MCP tool based on user input
        String toolResult = tryCallMcpTool(userInput);
        
        String invariantPrefix = getInvariantsAsPromptPrefix(currentProfile);
        
        // If we have tool result, add it to the context
        String planPrompt;
        if (toolResult != null && !toolResult.startsWith("Error:") && !toolResult.startsWith("No tool")) {
            // User asked for something we can fulfill via MCP tools
            planPrompt = invariantPrefix + 
                "You are a planning AI. Based on the user's request, create a step-by-step plan. " +
                "The following data has been retrieved from an external source and should be used in your response:\n" +
                toolResult + "\n\n" +
                "User's request: \"" + userInput + "\". Respond with the plan only.";
        } else {
            String planPromptBase = invariantPrefix + "You are a planning AI. Based on the user's request, create a step-by-step plan. The user's request is: \"" + userInput + "\". Respond with the plan only.";
            planPrompt = planPromptBase;
        }
        
        GigaChatComplexResponse planResponse = gigaChatApiService.getCompletion(new JSONArray(), planPrompt);
        String plan = planResponse.getFullResponse();
        conversationState.setLastPlan(plan);
        conversationState.setStage(Stage.AWAITING_PLAN_APPROVAL);

        ChatResponse response = new ChatResponse(plan);
        response.setResponseType(ResponseType.PLAN);
        response.setRequiresConfirmation(true);
        return response;
    }

    /**
     * Try to call an MCP tool if the user input matches a known tool pattern
     * @return tool result or null if no tool should be called
     */
    private String tryCallMcpTool(String userInput) {
        // Auto-connect if not already connected
        if (!mcpService.isConnected()) {
            boolean connected = mcpService.initializeConnection();
            if (!connected) {
                return null; // Connection failed, fall back to LLM-only mode
            }
        }

        String input = userInput.toLowerCase();

        // Weather tool detection
        if (input.contains("weather") || input.contains("погода") || input.contains("temperature") ||
            input.contains("температура") || (input.contains("how") && input.contains("hot") && input.contains("outside")) ||
            (input.contains("what") && input.contains("weather"))) {

            // Extract city name - simple heuristic: look for "in <city>" pattern
            String city = extractCityFromInput(userInput);
            if (city != null) {
                System.out.println("call tool get_weather for city " + city);
                return mcpService.callTool("get_weather", Map.of("city", city));
            }
        }

        return null;
    }

    /**
     * Extract city name from user input using simple heuristics
     */
    private String extractCityFromInput(String input) {
        // Look for patterns like "in Moscow", "for London", "weather in Tokyo"
        String[] patterns = {"in ", "for ", "at "};
        for (String pattern : patterns) {
            int index = input.toLowerCase().indexOf(pattern);
            if (index != -1) {
                String afterPattern = input.substring(index + pattern.length()).trim();
                // Remove trailing punctuation and question marks
                afterPattern = afterPattern.replaceAll("[?!.]+$", "").trim();
                // Take first 1-3 words as city name
                String[] words = afterPattern.split("\\s+");
                int cityWords = Math.min(words.length, 3);
                if (cityWords > 0) {
                    StringBuilder city = new StringBuilder();
                    for (int i = 0; i < cityWords; i++) {
                        if (i > 0) city.append(" ");
                        city.append(words[i]);
                    }
                    return city.toString().trim();
                }
            }
        }
        
        // If no pattern found, try to use the whole input after removing weather-related words
        String cleaned = input.replaceAll("(?i)(what('s| is)|how('s| is)|the|weather|like|outside|today|current|tell|me|about|for|in)", "").trim();
        cleaned = cleaned.replaceAll("[?!.]+$", "").trim();
        if (!cleaned.isEmpty() && cleaned.length() > 1) {
            return cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1);
        }
        
        return null;
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
                weatherLogRepository.deleteAll(profileLogin);
                currentProfile.setCurrentBranch(1);
                profileRepository.update(currentProfile);
                return "All history, invariants, and weather logs for profile " + profileLogin + " cleared and branch reset to 1.";
            case "/get_weather_start":
                return handleGetWeatherStart(command, currentProfile);
            case "/get_weather_stop":
                return handleGetWeatherStop(currentProfile);
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
            case "/mcp_list":
                return listMcpServersFormatted();
            case "/mcp_status":
                return mcpService.getConnectionStatus();
            case "/mcp_connect":
                boolean success = mcpService.initializeConnection();
                return success ? "MCP connection established successfully." : "Failed to establish MCP connection. Check logs for details.";
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

    @Override
    public List<String> listMcpServers() {
        return mcpService.listMcpServers();
    }

    private String listMcpServersFormatted() {
        List<String> servers = mcpService.listMcpServers();
        if (servers.isEmpty()) {
            return "No MCP servers found.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Available MCP Servers and Resources:\n");
        for (String server : servers) {
            sb.append("  ").append(server).append("\n");
        }
        return sb.toString();
    }

    private String handleGetWeatherStart(String command, Profile currentProfile) {
        String profileLogin = currentProfile.getLogin();

        // Check if already running
        if (weatherSchedulerService.isRunning(profileLogin)) {
            return "Weather scheduler is already running. Use /get_weather_stop to stop it first.";
        }

        // Parse arguments: [-t city] [-p period] [-s summaryCount]
        WeatherCommandArgs args = parseWeatherCommandArgs(command);

        // Start the scheduler
        weatherSchedulerService.startWeatherScheduler(
                profileLogin,
                args.city,
                args.period,
                args.summaryCount
        );

        WeatherSchedulerConfig config = weatherSchedulerService.getConfig(profileLogin);
        return String.format("Weather scheduler started for '%s' every %s. Showing last %d results after each fetch.",
                config.getCity(),
                config.getPeriodDescription(),
                config.getSummaryCount());
    }

    private String handleGetWeatherStop(Profile currentProfile) {
        String profileLogin = currentProfile.getLogin();
        boolean stopped = weatherSchedulerService.stopWeatherScheduler(profileLogin);
        if (stopped) {
            return "Weather scheduler stopped.";
        }
        return "No weather scheduler is currently running.";
    }

    private static class WeatherCommandArgs {
        String city = "Moscow";
        Duration period = Duration.ofHours(1);
        int summaryCount = 3;
    }

    private WeatherCommandArgs parseWeatherCommandArgs(String command) {
        WeatherCommandArgs args = new WeatherCommandArgs();

        // Skip the command name
        String remainder = command.substring("/get_weather_start".length()).trim();
        if (remainder.isEmpty()) {
            return args;
        }

        // Parse flags: -t <city>, -p <period>, -s <count>
        String[] tokens = remainder.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.equals("-t") && i + 1 < tokens.length) {
                args.city = tokens[++i];
            } else if (token.equals("-p") && i + 1 < tokens.length) {
                args.period = parsePeriod(tokens[++i]);
            } else if (token.equals("-s") && i + 1 < tokens.length) {
                try {
                    args.summaryCount = Integer.parseInt(tokens[++i]);
                    if (args.summaryCount < 1) {
                        args.summaryCount = 1;
                    }
                } catch (NumberFormatException e) {
                    // ignore, use default
                }
            }
        }

        return args;
    }

    private Duration parsePeriod(String periodStr) {
        if (periodStr.isEmpty()) {
            return Duration.ofHours(1);
        }

        // Try to parse as number with suffix: e.g., 10s, 5m, 1h, 1d
        char suffix = Character.toLowerCase(periodStr.charAt(periodStr.length() - 1));
        String numPart = periodStr;
        if (suffix == 's' || suffix == 'm' || suffix == 'h' || suffix == 'd') {
            numPart = periodStr.substring(0, periodStr.length() - 1);
        }

        long value;
        try {
            value = Long.parseLong(numPart);
        } catch (NumberFormatException e) {
            return Duration.ofHours(1); // default
        }

        return switch (suffix) {
            case 's' -> Duration.ofSeconds(value);
            case 'm' -> Duration.ofMinutes(value);
            case 'h' -> Duration.ofHours(value);
            case 'd' -> Duration.ofDays(value);
            default -> Duration.ofHours(value); // default to hours if no suffix
        };
    }

    @Override
    public String startWeatherScheduler(String profileLogin, String city, Duration period, int summaryCount) {
        weatherSchedulerService.startWeatherScheduler(profileLogin, city, period, summaryCount);
        WeatherSchedulerConfig config = weatherSchedulerService.getConfig(profileLogin);
        return String.format("Weather scheduler started for '%s' every %s.", config.getCity(), config.getPeriodDescription());
    }

    @Override
    public String stopWeatherScheduler(String profileLogin) {
        boolean stopped = weatherSchedulerService.stopWeatherScheduler(profileLogin);
        return stopped ? "Weather scheduler stopped." : "No weather scheduler was running.";
    }

    @Override
    public List<WeatherLog> getRecentWeatherLogs(String profileLogin, int limit) {
        return weatherLogRepository.findRecent(profileLogin, limit);
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
