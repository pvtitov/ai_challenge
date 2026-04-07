package com.github.pvtitov.aichat.service;

import java.util.List;

public interface McpService {
    List<String> listMcpServers();
    boolean initializeConnection();
    boolean isConnected();
    String getConnectionStatus();
}
