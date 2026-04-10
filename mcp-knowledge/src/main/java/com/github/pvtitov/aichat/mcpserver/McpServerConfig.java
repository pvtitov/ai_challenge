package com.github.pvtitov.aichat.mcpserver;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class McpServerConfig {

    @Bean
    public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider() {
        return WebMvcSseServerTransportProvider.builder()
                .messageEndpoint("/mcp/message")
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcSseServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }

    @Bean
    public io.modelcontextprotocol.server.McpSyncServer mcpSyncServer(WebMvcSseServerTransportProvider transportProvider) {

        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        // Tool 1: save_knowledge
        McpSchema.JsonSchema saveKnowledgeSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "title", Map.of("type", "string", "description", "Short title of the knowledge, often starting with 'How to' or 'How'"),
                        "description", Map.of("type", "string", "description", "Full description of the knowledge, formatted as markdown")
                ),
                List.of("title", "description"),
                null,
                null,
                null
        );
        McpSchema.Tool saveKnowledgeTool = McpSchema.Tool.builder()
                .name("save_knowledge")
                .description("Save a piece of knowledge with a title and full description. Title should be concise, e.g., 'How to run and close daemon process'. Description should be detailed and formatted as markdown.")
                .inputSchema(saveKnowledgeSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(saveKnowledgeTool, (exchange, request) -> {
            try {
                String title = (String) request.arguments().get("title");
                String description = (String) request.arguments().get("description");

                if (title == null || title.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: title is required")),
                            true, null, null
                    );
                }
                if (description == null || description.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: description is required")),
                            true, null, null
                    );
                }

                boolean success = KnowledgeRepository.saveKnowledge(title.trim(), description.trim());
                if (success) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Knowledge saved successfully: \"" + title + "\"")),
                            false, null, null
                    );
                } else {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: Failed to save knowledge")),
                            true, null, null
                    );
                }
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error saving knowledge: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        // Tool 2: knowledge_contents
        McpSchema.JsonSchema knowledgeContentsSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "regex", Map.of("type", "string", "description", "Optional regex pattern to filter titles. If omitted, returns all titles.")
                ),
                List.of(),
                null,
                null,
                null
        );
        McpSchema.Tool knowledgeContentsTool = McpSchema.Tool.builder()
                .name("knowledge_contents")
                .description("Return a list of all knowledge titles, or titles matching a regex pattern if provided.")
                .inputSchema(knowledgeContentsSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(knowledgeContentsTool, (exchange, request) -> {
            try {
                String regex = (String) request.arguments().get("regex");
                List<String> titles = KnowledgeRepository.getKnowledgeContents(regex);

                if (titles.isEmpty()) {
                    String msg = regex != null && !regex.isEmpty()
                            ? "No knowledge entries matching regex: " + regex
                            : "No knowledge entries found.";
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(msg)),
                            false, null, null
                    );
                }

                StringBuilder sb = new StringBuilder();
                if (regex != null && !regex.isEmpty()) {
                    sb.append("Knowledge titles matching regex \"").append(regex).append("\":\n");
                } else {
                    sb.append("All knowledge titles:\n");
                }
                for (String title : titles) {
                    sb.append("- ").append(title).append("\n");
                }

                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(sb.toString())),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error retrieving knowledge contents: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        // Tool 3: find_knowledge
        McpSchema.JsonSchema findKnowledgeSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "regex", Map.of("type", "string", "description", "Regex pattern to match against knowledge titles")
                ),
                List.of("regex"),
                null,
                null,
                null
        );
        McpSchema.Tool findKnowledgeTool = McpSchema.Tool.builder()
                .name("find_knowledge")
                .description("Find and return the first knowledge entry (title and full description) whose title matches the provided regex pattern.")
                .inputSchema(findKnowledgeSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(findKnowledgeTool, (exchange, request) -> {
            try {
                String regex = (String) request.arguments().get("regex");

                if (regex == null || regex.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: regex parameter is required")),
                            true, null, null
                    );
                }

                List<KnowledgeRepository.KnowledgeEntry> entries = KnowledgeRepository.findKnowledge(regex.trim());

                if (entries.isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("No knowledge found matching regex: " + regex)),
                            false, null, null
                    );
                }

                KnowledgeRepository.KnowledgeEntry entry = entries.get(0);
                String result = "## " + entry.getTitle() + "\n\n" + entry.getDescription();

                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result)),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error finding knowledge: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        return McpServer.sync(transportProvider)
                .serverInfo("Knowledge MCP Server", "1.0.0")
                .instructions("This MCP server provides knowledge management capabilities. Use save_knowledge to store information, knowledge_contents to list titles, and find_knowledge to retrieve specific knowledge entries.")
                .tools(tools)
                .build();
    }
}
