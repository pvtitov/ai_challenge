package com.github.pvtitov.aichatgithub.service;

import com.github.pvtitov.aichatgithub.constants.ApiConstants;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with GitHub repositories via MCP server.
 * Uses proper MCP SSE protocol for communication.
 */
public class GitHubMcpService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMcpService.class);

    private final String mcpServerUrl;
    private final String repoPath;
    private McpSyncClient mcpClient;
    private boolean isReady = false;

    public GitHubMcpService() {
        this.mcpServerUrl = ApiConstants.GITHUB_MCP_SERVER_URL;
        this.repoPath = ApiConstants.TINYAI_REPO_PATH;
        initializeMcpClient();
    }

    /**
     * Initialize MCP client connection to the server.
     */
    private void initializeMcpClient() {
        try {
            System.out.println("[GitHub MCP: Connecting to " + mcpServerUrl + "...]");
            
            // Create SSE transport
            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(mcpServerUrl).build();
            
            // Create MCP client
            mcpClient = McpClient.sync(transport).build();
            
            // Initialize connection
            McpSchema.InitializeResult initResult = mcpClient.initialize();
            
            if (initResult != null) {
                isReady = true;
                System.out.println("[GitHub MCP: ✓ Connected to " + mcpServerUrl + "]");
                logger.info("GitHub MCP server connected: {}", initResult.serverInfo());
            } else {
                System.out.println("[GitHub MCP: ✗ Failed to initialize connection]");
                isReady = false;
            }
        } catch (Exception e) {
            System.out.println("[GitHub MCP: ✗ Not available (" + e.getMessage() + ")]");
            logger.warn("GitHub MCP server not available: {}", e.getMessage());
            isReady = false;
        }
    }

    /**
     * Check if the service is ready.
     */
    public boolean isReady() {
        return isReady && mcpClient != null;
    }

    /**
     * List recent commits from the repository.
     */
    public List<Map<String, String>> getCommitHistory(String branch, int limit) {
        if (!isReady()) {
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("message", "[GitHub MCP: Server not available]");
            return List.of(errorMap);
        }

        try {
            System.out.println("[GitHub MCP: Calling list_commits tool]");
            System.out.println("[GitHub MCP: Repository: " + repoPath + "]");
            System.out.println("[GitHub MCP: Max commits: " + limit + "]");
            
            // Call the list_commits tool
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("repo_path", repoPath);
            arguments.put("max_count", String.valueOf(limit));

            McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("list_commits", arguments)
            );

            if (result.isError()) {
                String errorText = extractTextContent(result);
                System.out.println("[GitHub MCP: ✗ Error from list_commits: " + errorText + "]");
                logger.error("Error calling list_commits: {}", errorText);
                Map<String, String> errorMap = new HashMap<>();
                errorMap.put("message", "[GitHub MCP: Error - " + errorText + "]");
                return List.of(errorMap);
            }

            // Parse the result text into commit entries
            String textContent = extractTextContent(result);
            List<Map<String, String>> commits = parseCommitsText(textContent);
            
            System.out.println("[GitHub MCP: ✓ Successfully parsed " + commits.size() + " commits]");
            if (!commits.isEmpty()) {
                System.out.println("[GitHub MCP: Latest commit: " + commits.get(0).getOrDefault("message", "unknown") + "]");
            }
            
            return commits;
        } catch (Exception e) {
            System.out.println("[GitHub MCP: ✗ Exception in list_commits: " + e.getMessage() + "]");
            logger.error("Failed to get commit history: {}", e.getMessage());
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("message", "[GitHub MCP: Error - " + e.getMessage() + "]");
            return List.of(errorMap);
        }
    }

    /**
     * List branches in the repository.
     */
    public List<String> listBranches() {
        if (!isReady()) {
            return List.of("[GitHub MCP: Server not available]");
        }

        try {
            System.out.println("[GitHub MCP: Calling list_branches tool]");
            System.out.println("[GitHub MCP: Repository: " + repoPath + "]");
            
            // Call the list_branches tool
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("repo_path", repoPath);

            McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("list_branches", arguments)
            );

            if (result.isError()) {
                String errorText = extractTextContent(result);
                System.out.println("[GitHub MCP: ✗ Error from list_branches: " + errorText + "]");
                logger.error("Error calling list_branches: {}", errorText);
                return List.of("[GitHub MCP: Error - " + errorText + "]");
            }

            // Parse the result text into branch names
            String textContent = extractTextContent(result);
            List<String> branches = parseBranchesText(textContent);
            
            System.out.println("[GitHub MCP: ✓ Found " + branches.size() + " branches]");
            if (!branches.isEmpty()) {
                System.out.println("[GitHub MCP: Branches: " + String.join(", ", branches) + "]");
            }
            
            return branches;
        } catch (Exception e) {
            System.out.println("[GitHub MCP: ✗ Exception in list_branches: " + e.getMessage() + "]");
            logger.error("Failed to list branches: {}", e.getMessage());
            return List.of("[GitHub MCP: Error - " + e.getMessage() + "]");
        }
    }

    /**
     * Read a file from the repository.
     */
    public String readFile(String path, String ref) {
        if (!isReady()) {
            return "[GitHub MCP: Server not available - cannot read " + path + "]";
        }

        try {
            System.out.println("[GitHub MCP: Calling read_file_contents tool]");
            System.out.println("[GitHub MCP: File: " + path + "]");
            
            // Call the read_file_contents tool
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("repo_path", repoPath);
            arguments.put("file_path", path);

            McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("read_file_contents", arguments)
            );

            if (result.isError()) {
                String errorText = extractTextContent(result);
                System.out.println("[GitHub MCP: ✗ Error reading " + path + ": " + errorText + "]");
                logger.error("Error calling read_file_contents: {}", errorText);
                return "[GitHub MCP: Error - " + errorText + "]";
            }

            String content = extractTextContent(result);
            System.out.println("[GitHub MCP: ✓ Read " + path + " (" + content.length() + " bytes)]");
            return content;
        } catch (Exception e) {
            System.out.println("[GitHub MCP: ✗ Exception reading " + path + ": " + e.getMessage() + "]");
            logger.error("Failed to read file: {}", e.getMessage());
            return "[GitHub MCP: Error - " + e.getMessage() + "]";
        }
    }

    /**
     * Get repository structure.
     */
    public String getRepoStructure(int maxDepth) {
        if (!isReady()) {
            return "[GitHub MCP: Server not available]";
        }

        try {
            System.out.println("[GitHub MCP: Calling get_repo_structure tool]");
            System.out.println("[GitHub MCP: Repository: " + repoPath + "]");
            System.out.println("[GitHub MCP: Max depth: " + maxDepth + "]");
            
            // Call the get_repo_structure tool
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("repo_path", repoPath);
            arguments.put("max_depth", String.valueOf(maxDepth));

            McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("get_repo_structure", arguments)
            );

            if (result.isError()) {
                String errorText = extractTextContent(result);
                System.out.println("[GitHub MCP: ✗ Error getting structure: " + errorText + "]");
                logger.error("Error calling get_repo_structure: {}", errorText);
                return "[GitHub MCP: Error - " + errorText + "]";
            }

            String structure = extractTextContent(result);
            System.out.println("[GitHub MCP: ✓ Got repository structure (" + structure.length() + " bytes)]");
            return structure;
        } catch (Exception e) {
            System.out.println("[GitHub MCP: ✗ Exception getting structure: " + e.getMessage() + "]");
            logger.error("Failed to get repo structure: {}", e.getMessage());
            return "[GitHub MCP: Error - " + e.getMessage() + "]";
        }
    }

    /**
     * Extract text content from MCP tool result.
     */
    private String extractTextContent(McpSchema.CallToolResult result) {
        StringBuilder sb = new StringBuilder();
        if (result.content() != null) {
            for (McpSchema.Content content : result.content()) {
                if (content instanceof McpSchema.TextContent textContent) {
                    sb.append(textContent.text());
                }
            }
        }
        return sb.toString();
    }

    /**
     * Parse commits text into structured data.
     */
    private List<Map<String, String>> parseCommitsText(String text) {
        List<Map<String, String>> commits = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return commits;
        }

        // Split by "Hash:" to get individual commits
        String[] parts = text.split("Hash: ");
        for (int i = 1; i < parts.length; i++) {
            String commitText = parts[i];
            Map<String, String> commit = new HashMap<>();
            
            String[] lines = commitText.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("Hash: ")) {
                    commit.put("hash", line.substring(6));
                } else if (line.startsWith("Author: ")) {
                    commit.put("author", line.substring(8));
                } else if (line.startsWith("Date: ")) {
                    commit.put("date", line.substring(6));
                } else if (line.startsWith("Message: ")) {
                    commit.put("message", line.substring(9));
                }
            }
            
            if (!commit.isEmpty()) {
                commits.add(commit);
            }
        }
        
        return commits;
    }

    /**
     * Parse branches text into list of branch names.
     */
    private List<String> parseBranchesText(String text) {
        List<String> branches = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return branches;
        }

        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("- ")) {
                branches.add(line.substring(2));
            }
        }
        
        return branches;
    }

    /**
     * Close the MCP client connection.
     */
    public void close() {
        if (mcpClient != null) {
            try {
                mcpClient.close();
                System.out.println("[GitHub MCP: Connection closed]");
            } catch (Exception e) {
                logger.error("Error closing MCP client: {}", e.getMessage());
            }
        }
    }
}
