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
        
        // Define tools
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        
        // Tool 1: Weather lookup
        McpSchema.JsonSchema weatherSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "city", Map.of("type", "string", "description", "City name"),
                        "country", Map.of("type", "string", "description", "Country code (e.g., US, RU)")
                ),
                List.of("city"),
                null,
                null,
                null
        );
        McpSchema.Tool weatherTool = McpSchema.Tool.builder()
                .name("get_weather")
                .description("Get current weather information for a city")
                .inputSchema(weatherSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(weatherTool, (exchange, request) -> {
            String city = (String) request.arguments().get("city");
            String country = (String) request.arguments().getOrDefault("country", "US");
            String weather = String.format("Weather in %s, %s: Sunny, 22°C (72°F), Humidity: 45%%", city, country);
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(weather)),
                    false,
                    null,
                    null
            );
        }));

        // Tool 2: Calculator
        McpSchema.JsonSchema calculatorSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("expression", Map.of("type", "string", "description", "Mathematical expression to evaluate")),
                List.of("expression"),
                null,
                null,
                null
        );
        McpSchema.Tool calculatorTool = McpSchema.Tool.builder()
                .name("calculate")
                .description("Perform mathematical calculations")
                .inputSchema(calculatorSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(calculatorTool, (exchange, request) -> {
            String expression = (String) request.arguments().get("expression");
            String result = "Result of '" + expression + "': 42";
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(result)),
                    false,
                    null,
                    null
            );
        }));

        // Tool 3: Current time
        McpSchema.JsonSchema timeSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("timezone", Map.of("type", "string", "description", "Timezone (e.g., UTC, Europe/Moscow)")),
                null,
                null,
                null,
                null
        );
        McpSchema.Tool timeTool = McpSchema.Tool.builder()
                .name("get_current_time")
                .description("Get the current date and time")
                .inputSchema(timeSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(timeTool, (exchange, request) -> {
            String timezone = (String) request.arguments().getOrDefault("timezone", "UTC");
            String currentTime = "Current time in " + timezone + ": " + java.time.LocalDateTime.now().toString();
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(currentTime)),
                    false,
                    null,
                    null
            );
        }));

        // Define resources
        List<McpServerFeatures.SyncResourceSpecification> resources = new ArrayList<>();
        
        McpSchema.Resource docResource = McpSchema.Resource.builder()
                .uri("file:///docs/readme.md")
                .name("README Documentation")
                .description("Project README file with overview and setup instructions")
                .mimeType("markdown")
                .build();
        resources.add(new McpServerFeatures.SyncResourceSpecification(docResource, (exchange, uri) -> {
            String content = "# Project Overview\n\nThis is a test MCP server with sample tools and resources.\n\n## Tools\n- get_weather: Weather lookup\n- calculate: Math calculations\n- get_current_time: Current time\n";
            return new McpSchema.ReadResourceResult(
                    List.of(new McpSchema.TextResourceContents(uri.toString(), "markdown", content))
            );
        }));

        McpSchema.Resource configResource = McpSchema.Resource.builder()
                .uri("file:///config/settings.json")
                .name("Configuration Settings")
                .description("Application configuration and settings")
                .mimeType("json")
                .build();
        resources.add(new McpServerFeatures.SyncResourceSpecification(configResource, (exchange, uri) -> {
            String content = "{\n  \"appName\": \"MCP Test Server\",\n  \"version\": \"1.0\",\n  \"environment\": \"development\"\n}";
            return new McpSchema.ReadResourceResult(
                    List.of(new McpSchema.TextResourceContents(uri.toString(), "json", content))
            );
        }));

        // Define prompts
        List<McpServerFeatures.SyncPromptSpecification> prompts = new ArrayList<>();
        
        McpSchema.Prompt summaryPrompt = new McpSchema.Prompt(
                "summarize_text",
                "Summarize a given text into key points",
                List.of(
                        new McpSchema.PromptArgument("text", "The text to summarize", true),
                        new McpSchema.PromptArgument("max_length", "Maximum length of summary (optional)", false)
                )
        );
        prompts.add(new McpServerFeatures.SyncPromptSpecification(summaryPrompt, (exchange, request) -> {
            String text = (String) request.arguments().get("text");
            return new McpSchema.GetPromptResult(
                    "Text Summary Prompt",
                    List.of(
                            new McpSchema.PromptMessage(
                                    McpSchema.Role.USER,
                                    new McpSchema.TextContent("Please summarize the following text into key points:\n\n" + text)
                            )
                    )
            );
        }));

        McpSchema.Prompt codeReviewPrompt = new McpSchema.Prompt(
                "code_review",
                "Review code for best practices and potential issues",
                List.of(
                        new McpSchema.PromptArgument("code", "The code to review", true),
                        new McpSchema.PromptArgument("language", "Programming language", false)
                )
        );
        prompts.add(new McpServerFeatures.SyncPromptSpecification(codeReviewPrompt, (exchange, request) -> {
            String code = (String) request.arguments().get("code");
            String language = (String) request.arguments().getOrDefault("language", "unknown");
            return new McpSchema.GetPromptResult(
                    "Code Review Prompt",
                    List.of(
                            new McpSchema.PromptMessage(
                                    McpSchema.Role.USER,
                                    new McpSchema.TextContent("Please review the following " + language + " code for best practices, potential bugs, and improvements:\n\n" + code)
                            )
                    )
            );
        }));

        // Build server
        return McpServer.sync(transportProvider)
                .serverInfo("Test MCP Server", "1.0.0")
                .instructions("This is a test MCP server providing sample tools, resources, and prompts for development and testing.")
                .tools(tools)
                .resources(resources)
                .prompts(prompts)
                .build();
    }
}
