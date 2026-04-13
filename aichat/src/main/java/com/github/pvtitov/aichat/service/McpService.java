package com.github.pvtitov.aichat.service;

import java.util.List;
import java.util.Map;

public interface McpService {
    List<String> listMcpServers();
    boolean initializeConnection();
    boolean isConnected();
    String getConnectionStatus();
    String callTool(String toolName, Map<String, Object> arguments);

    // Knowledge server methods
    boolean initializeKnowledgeConnection();
    boolean isKnowledgeConnected();
    String callKnowledgeTool(String toolName, Map<String, Object> arguments);

    // GitHub server methods
    boolean initializeGitHubConnection();
    boolean isGitHubConnected();
    String callGitHubTool(String toolName, Map<String, Object> arguments);
}
