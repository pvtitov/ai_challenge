package com.github.pvtitov.aichatclearning.server;

import com.github.pvtitov.aichatclearning.dto.LlmStructuredResponse;
import com.github.pvtitov.aichatclearning.service.ChatService;
import com.github.pvtitov.aichatclearning.service.ChatServiceImpl;
import com.github.pvtitov.aichatclearning.service.LlmModel;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

/**
 * Represents a single terminal session with its own ChatService instance.
 * Handles command processing and output routing to the WebSocket connection.
 */
public class TerminalSession {

    private static final Logger logger = LoggerFactory.getLogger(TerminalSession.class);

    private static final String COMMAND_QUIT = "/quit";
    private static final String COMMAND_CLEAN = "/clean";
    private static final String COMMAND_MODEL = "/model";

    private final Session jettySession;
    private final ChatService chatService;
    private volatile boolean running = true;
    private String sessionId;

    public TerminalSession(Session jettySession) {
        this.jettySession = jettySession;
        this.chatService = new ChatServiceImpl();
        this.sessionId = jettySession.getRemoteAddress().toString();
    }

    public Session getJettySession() {
        return jettySession;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void processInput(String input) {
        if (!running) {
            return;
        }

        if (COMMAND_QUIT.equalsIgnoreCase(input)) {
            sendOutput("Goodbye!\n");
            running = false;
            // Close the WebSocket connection
            if (jettySession.isOpen()) {
                try {
                    jettySession.close();
                } catch (Exception e) {
                    logger.error("Error closing session", e);
                }
            }
            return;
        }

        if (COMMAND_CLEAN.equalsIgnoreCase(input)) {
            chatService.clearHistory();
            sendOutput("Dialog history cleared.\n");
            return;
        }

        if (input.equalsIgnoreCase(COMMAND_MODEL)) {
            printAvailableModels();
            return;
        }

        if (input.toLowerCase().startsWith(COMMAND_MODEL + " ")) {
            String modelName = input.substring(COMMAND_MODEL.length()).trim();
            switchModel(modelName);
            return;
        }

        // Regular chat message
        try {
            // Capture System.out during processing
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream capture = new PrintStream(baos);
            PrintStream originalOut = System.out;
            System.setOut(capture);

            try {
                LlmStructuredResponse response = chatService.processMessage(input);

                // Flush the captured output
                System.out.flush();
                String capturedOutput = baos.toString();

                // Restore original stdout
                System.setOut(originalOut);

                // Send all captured output to the client
                if (!capturedOutput.isEmpty()) {
                    sendOutput(capturedOutput);
                }

                // Print warning if JSON parsing failed
                if (response.isJsonParseFailed()) {
                    sendOutput("\n[Warning: Failed to parse structured response.]\n");
                }

                sendOutput("\n");

            } catch (Exception e) {
                System.setOut(originalOut);
                sendOutput("Error processing request: " + e.getMessage() + "\n");
                logger.error("Error processing message", e);
            }
        } catch (Exception e) {
            sendOutput("Error: " + e.getMessage() + "\n");
            logger.error("Error processing input", e);
        }
    }

    private void printAvailableModels() {
        List<LlmModel> models = chatService.getLlmServiceRegistry().getAvailableModels();
        String currentModelId = chatService.getLlmServiceRegistry().getCurrentModelId();

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Available Models ===\n");
        if (models.isEmpty()) {
            sb.append("No models available.\n");
        } else {
            for (LlmModel model : models) {
                String marker = model.getId().equals(currentModelId) ? " [CURRENT]" : "";
                sb.append("  - ").append(model.getId()).append(": ").append(model.getName()).append(marker).append("\n");
            }
        }
        sb.append("========================\n\n");
        sendOutput(sb.toString());
    }

    private void switchModel(String modelName) {
        boolean switched = chatService.getLlmServiceRegistry().switchToModelByName(modelName);
        if (switched) {
            String currentModelId = chatService.getLlmServiceRegistry().getCurrentModelId();
            sendOutput("Switched to model: " + currentModelId + "\n");
        } else {
            switched = chatService.getLlmServiceRegistry().setCurrentModel(modelName);
            if (switched) {
                sendOutput("Switched to model: " + modelName + "\n");
            } else {
                sendOutput("Model '" + modelName + "' not found. Use /model to see available models.\n");
            }
        }
        sendOutput("\n");
    }

    /**
     * Send output to the WebSocket client.
     */
    public void sendOutput(String text) {
        if (jettySession != null && jettySession.isOpen()) {
            try {
                jettySession.getRemote().sendString(text);
            } catch (Exception e) {
                logger.error("Failed to send output to client", e);
            }
        }
    }

    /**
     * Send the prompt indicator to the client.
     */
    public void sendPrompt() {
        sendOutput("\u0001PROMPT\u0001");
    }

    /**
     * Shutdown the session and cleanup resources.
     */
    public void shutdown() {
        running = false;
        chatService.shutdown();
    }
}
