package com.github.pvtitov.aichatclearning;

import com.github.pvtitov.aichatclearning.server.ChatServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server entry point for running AI Chat Lite as an HTTP server.
 *
 * Usage:
 *   java -cp aichat-c-learning-1.0.jar com.github.pvtitov.aichatclearning.ServerMain [port]
 *
 * Default port is 8080. The server binds to all network interfaces (0.0.0.0)
 * making it accessible from the local network.
 */
public class ServerMain {

    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) {
        int port = 8080;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1 || port > 65535) {
                    System.err.println("Invalid port: " + port + ". Must be between 1 and 65535.");
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid port argument: " + args[0]);
                System.exit(1);
            }
        }

        ChatServer server = new ChatServer(port);

        try {
            server.start();
            server.join();
        } catch (Exception e) {
            logger.error("Server failed to start", e);
            System.err.println("Server failed to start: " + e.getMessage());
            System.exit(1);
        }
    }
}
