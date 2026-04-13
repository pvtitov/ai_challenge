package com.github.pvtitov.aichat.service;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class McpServiceImpl implements McpService {

    private static final Logger log = LoggerFactory.getLogger(McpServiceImpl.class);

    private final Function<String, McpSyncClient> mcpClientFactory;

    @Value("${mcp.server.url:http://localhost:8081}")
    private String mcpServerUrl;

    @Value("${mcp.knowledge.server.url:http://localhost:8082}")
    private String mcpKnowledgeServerUrl;

    @Value("${mcp.github.server.url:http://localhost:8083}")
    private String mcpGitHubServerUrl;

    private volatile McpSyncClient mcpClient;
    private volatile boolean connected = false;
    private final ReentrantLock connectionLock = new ReentrantLock();

    // Knowledge server client
    private volatile McpSyncClient mcpKnowledgeClient;
    private volatile boolean knowledgeConnected = false;
    private final ReentrantLock knowledgeConnectionLock = new ReentrantLock();

    // GitHub server client
    private volatile McpSyncClient mcpGitHubClient;
    private volatile boolean gitHubConnected = false;
    private final ReentrantLock gitHubConnectionLock = new ReentrantLock();

    public McpServiceImpl(Function<String, McpSyncClient> mcpClientFactory) {
        this.mcpClientFactory = mcpClientFactory;
    }

    @Override
    public List<String> listMcpServers() {
        List<String> servers = new ArrayList<>();

        try {
            // Initialize connection if not already done
            if (!connected) {
                boolean initSuccess = initializeConnection();
                if (!initSuccess) {
                    servers.add("Failed to connect to MCP server at " + mcpServerUrl);
                    servers.add("Please ensure an MCP server is running at the configured URL");
                    servers.add("Use /mcp_connect to retry connection");
                    return servers;
                }
            }

            // List available tools from the MCP server
            try {
                McpSchema.ListToolsResult toolsResult = mcpClient.listTools();
                if (toolsResult != null && toolsResult.tools() != null && !toolsResult.tools().isEmpty()) {
                    servers.add("Server: " + mcpServerUrl);
                    servers.add("Tools:");
                    List<String> toolNames = toolsResult.tools().stream()
                            .map(tool -> "  - " + tool.name() + ": " + (tool.description() != null ? tool.description() : "No description"))
                            .collect(Collectors.toList());
                    servers.addAll(toolNames);
                }
            } catch (Exception e) {
                log.warn("Failed to list tools: {}", e.getMessage());
                servers.add("Server: " + mcpServerUrl + " (connected)");
                servers.add("Failed to list tools: " + e.getMessage());
            }

            // List available resources from the MCP server
            try {
                McpSchema.ListResourcesResult resourcesResult = mcpClient.listResources();
                if (resourcesResult != null && resourcesResult.resources() != null && !resourcesResult.resources().isEmpty()) {
                    servers.add("Resources:");
                    List<String> resourceNames = resourcesResult.resources().stream()
                            .map(resource -> "  - " + resource.uri() + " (" + resource.name() + ")")
                            .collect(Collectors.toList());
                    servers.addAll(resourceNames);
                }
            } catch (Exception e) {
                log.warn("Failed to list resources: {}", e.getMessage());
            }

            // List available prompts from the MCP server
            try {
                McpSchema.ListPromptsResult promptsResult = mcpClient.listPrompts();
                if (promptsResult != null && promptsResult.prompts() != null && !promptsResult.prompts().isEmpty()) {
                    servers.add("Prompts:");
                    List<String> promptNames = promptsResult.prompts().stream()
                            .map(prompt -> "  - " + prompt.name() + ": " + (prompt.description() != null ? prompt.description() : ""))
                            .collect(Collectors.toList());
                    servers.addAll(promptNames);
                }
            } catch (Exception e) {
                log.warn("Failed to list prompts: {}", e.getMessage());
            }

            if (servers.isEmpty() && connected) {
                servers.add("Server: " + mcpServerUrl + " (connected, but no tools/resources/prompts available)");
            }

        } catch (Exception e) {
            log.error("Failed to list MCP servers: {}", e.getMessage(), e);
            servers.add("Error connecting to MCP server at " + mcpServerUrl + ": " + e.getMessage());
            connected = false;
        }

        return servers;
    }

    /**
     * Initialize connection to MCP server
     * @return true if connection established successfully
     */
    public boolean initializeConnection() {
        connectionLock.lock();
        try {
            // Close existing client if present
            if (mcpClient != null) {
                try {
                    mcpClient.close();
                } catch (Exception e) {
                    log.warn("Error closing existing MCP client: {}", e.getMessage());
                }
            }

            // Create a new client instance
            mcpClient = mcpClientFactory.apply(mcpServerUrl);
            connected = false;

            try {
                McpSchema.InitializeResult initResult = mcpClient.initialize();
                if (initResult != null) {
                    connected = true;
                    log.info("Successfully connected to MCP server. Server info: {}", initResult.serverInfo());
                    log.info("Server capabilities: {}", initResult.capabilities());
                    if (initResult.instructions() != null) {
                        log.info("Server instructions: {}", initResult.instructions());
                    }
                    return true;
                }
            } catch (Exception e) {
                log.error("Failed to initialize MCP connection: {}", e.getMessage(), e);
                System.err.println("MCP Connection Error: " + e.getMessage());
                e.printStackTrace();
                connected = false;
            }
            return false;
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Check if MCP connection is established
     * @return true if connected
     */
    public boolean isConnected() {
        return connected && mcpClient != null && mcpClient.isInitialized();
    }

    /**
     * Check if knowledge MCP connection is established
     * @return true if connected
     */
    public boolean isKnowledgeConnected() {
        return knowledgeConnected && mcpKnowledgeClient != null && mcpKnowledgeClient.isInitialized();
    }

    /**
     * Initialize connection to knowledge MCP server
     * @return true if connection established successfully
     */
    public boolean initializeKnowledgeConnection() {
        knowledgeConnectionLock.lock();
        try {
            if (mcpKnowledgeClient != null) {
                try {
                    mcpKnowledgeClient.close();
                } catch (Exception e) {
                    log.warn("Error closing existing knowledge MCP client: {}", e.getMessage());
                }
            }

            mcpKnowledgeClient = mcpClientFactory.apply(mcpKnowledgeServerUrl);
            knowledgeConnected = false;

            try {
                McpSchema.InitializeResult initResult = mcpKnowledgeClient.initialize();
                if (initResult != null) {
                    knowledgeConnected = true;
                    log.info("Successfully connected to Knowledge MCP server. Server info: {}", initResult.serverInfo());
                    return true;
                }
            } catch (Exception e) {
                log.error("Failed to initialize knowledge MCP connection: {}", e.getMessage(), e);
                knowledgeConnected = false;
            }
            return false;
        } finally {
            knowledgeConnectionLock.unlock();
        }
    }

    /**
     * Call a tool on the knowledge MCP server
     */
    public String callKnowledgeTool(String toolName, Map<String, Object> arguments) {
        knowledgeConnectionLock.lock();
        try {
            if (!isKnowledgeConnected()) {
                boolean initSuccess = initializeKnowledgeConnection();
                if (!initSuccess) {
                    return "Error: Cannot call knowledge tool - Knowledge MCP server is not connected";
                }
            }

            try {
                McpSchema.CallToolResult result = mcpKnowledgeClient.callTool(
                    new McpSchema.CallToolRequest(toolName, arguments)
                );

                if (result == null) {
                    return "Error: Knowledge tool '" + toolName + "' returned null result";
                }

                if (result.isError()) {
                    return "Error calling knowledge tool '" + toolName + "': " + extractContent(result);
                }

                return extractContent(result);
            } catch (Exception e) {
                log.error("Failed to call knowledge tool '{}': {}", toolName, e.getMessage(), e);
                return "Error calling knowledge tool '" + toolName + "': " + e.getMessage();
            }
        } finally {
            knowledgeConnectionLock.unlock();
        }
    }

    /**
     * Check if GitHub MCP connection is established
     * @return true if connected
     */
    public boolean isGitHubConnected() {
        return gitHubConnected && mcpGitHubClient != null && mcpGitHubClient.isInitialized();
    }

    /**
     * Initialize connection to GitHub MCP server
     * @return true if connection established successfully
     */
    public boolean initializeGitHubConnection() {
        gitHubConnectionLock.lock();
        try {
            if (mcpGitHubClient != null) {
                try {
                    mcpGitHubClient.close();
                } catch (Exception e) {
                    log.warn("Error closing existing GitHub MCP client: {}", e.getMessage());
                }
            }

            mcpGitHubClient = mcpClientFactory.apply(mcpGitHubServerUrl);
            gitHubConnected = false;

            try {
                McpSchema.InitializeResult initResult = mcpGitHubClient.initialize();
                if (initResult != null) {
                    gitHubConnected = true;
                    log.info("Successfully connected to GitHub MCP server. Server info: {}", initResult.serverInfo());
                    log.info("Server instructions: {}", initResult.instructions());
                    return true;
                }
            } catch (Exception e) {
                log.error("Failed to initialize GitHub MCP connection: {}", e.getMessage(), e);
                gitHubConnected = false;
            }
            return false;
        } finally {
            gitHubConnectionLock.unlock();
        }
    }

    /**
     * Call a tool on the GitHub MCP server
     */
    public String callGitHubTool(String toolName, Map<String, Object> arguments) {
        gitHubConnectionLock.lock();
        try {
            if (!isGitHubConnected()) {
                boolean initSuccess = initializeGitHubConnection();
                if (!initSuccess) {
                    return "Error: Cannot call GitHub tool - GitHub MCP server is not connected";
                }
            }

            try {
                McpSchema.CallToolResult result = mcpGitHubClient.callTool(
                    new McpSchema.CallToolRequest(toolName, arguments)
                );

                if (result == null) {
                    return "Error: GitHub tool '" + toolName + "' returned null result";
                }

                if (result.isError()) {
                    return "Error calling GitHub tool '" + toolName + "': " + extractContent(result);
                }

                return extractContent(result);
            } catch (Exception e) {
                log.error("Failed to call GitHub tool '{}': {}", toolName, e.getMessage(), e);
                return "Error calling GitHub tool '" + toolName + "': " + e.getMessage();
            }
        } finally {
            gitHubConnectionLock.unlock();
        }
    }

    /**
     * Get connection status message
     * @return status message
     */
    public String getConnectionStatus() {
        if (isConnected()) {
            McpSchema.ServerCapabilities capabilities = mcpClient.getServerCapabilities();
            McpSchema.Implementation serverInfo = mcpClient.getServerInfo();
            return "MCP Connection: ACTIVE\n" +
                   "Server: " + (serverInfo != null ? serverInfo.name() : "Unknown") + "\n" +
                   "Capabilities: " + (capabilities != null ? capabilities.toString() : "None");
        } else {
            return "MCP Connection: NOT ESTABLISHED\n" +
                   "Server URL: " + mcpServerUrl + "\n" +
                   "Use /mcp_connect to establish connection";
        }
    }

    /**
     * Call an MCP tool with the given arguments
     * @param toolName the name of the tool to call
     * @param arguments the arguments to pass to the tool
     * @return the tool result as a string, or error message
     */
    @Override
    public String callTool(String toolName, Map<String, Object> arguments) {
        connectionLock.lock();
        try {
            if (!isConnected()) {
                boolean initSuccess = initializeConnection();
                if (!initSuccess) {
                    return "Error: Cannot call tool - MCP server is not connected";
                }
            }

            try {
                McpSchema.CallToolResult result = mcpClient.callTool(
                    new McpSchema.CallToolRequest(toolName, arguments)
                );

                if (result == null) {
                    return "Error: Tool '" + toolName + "' returned null result";
                }

                if (result.isError()) {
                    return "Error calling tool '" + toolName + "': " + extractContent(result);
                }

                return extractContent(result);
            } catch (Exception e) {
                log.error("Failed to call tool '{}': {}", toolName, e.getMessage(), e);
                return "Error calling tool '" + toolName + "': " + e.getMessage();
            }
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Extract text content from tool result
     */
    private String extractContent(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "Tool returned empty content";
        }

        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                sb.append(textContent.text());
            } else {
                sb.append(content.toString());
            }
        }
        return sb.toString();
    }
}
