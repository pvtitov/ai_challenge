package com.github.pvtitov.aichatclearning.server;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebSocket handler that provides a terminal-like experience.
 * Each connected client gets its own TerminalSession.
 */
@WebSocket
public class TerminalWebSocket {

    private static final Logger logger = LoggerFactory.getLogger(TerminalWebSocket.class);
    private static final ConcurrentHashMap<String, TerminalSession> sessions = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private TerminalSession session;

    @OnWebSocketConnect
    public void onConnect(Session jettySession) {
        session = new TerminalSession(jettySession);
        String sessionId = session.getSessionId();
        logger.info("WebSocket connected: {}", sessionId);

        sessions.put(sessionId, session);

        // Send welcome banner
        session.sendOutput("========================================\n");
        session.sendOutput("  AI Chat Lite - Web Terminal\n");
        session.sendOutput("========================================\n");
        session.sendOutput("Commands:\n");
        session.sendOutput("  /quit    - Exit the application\n");
        session.sendOutput("  /clean   - Clear dialog history\n");
        session.sendOutput("  /model   - List available models\n");
        session.sendOutput("  /model <model> - Switch to a model\n");
        session.sendOutput("========================================\n\n");
        session.sendPrompt();
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
        if (session == null) {
            return;
        }

        String input = message.trim();
        if (input.isEmpty()) {
            session.sendPrompt();
            return;
        }

        // Echo the input
        session.sendOutput("You: " + input + "\n");

        // Process in a separate thread to not block WebSocket
        final String finalInput = input;
        executor.submit(() -> {
            try {
                session.processInput(finalInput);
            } catch (Exception e) {
                logger.error("Error processing input", e);
                session.sendOutput("Error: " + e.getMessage() + "\n");
            }
            session.sendPrompt();
        });
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        if (session != null) {
            String sessionId = session.getSessionId();
            logger.info("WebSocket disconnected: {} ({}: {})", sessionId, statusCode, reason);
            sessions.remove(sessionId);
            session.shutdown();
        }
    }

    @OnWebSocketError
    public void onError(Throwable error) {
        logger.error("WebSocket error", error);
    }
}
