package com.github.pvtitov.aichat.mcpserver;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    /**
     * Geocode city name to coordinates using Open-Meteo Geocoding API
     */
    private double[] geocodeCity(String cityName) throws Exception {
        String encodedCity = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
        String geocodingUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" + encodedCity + "&count=1";
        
        URL url = new URL(geocodingUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();
        
        String jsonResponse = response.toString();
        
        if (jsonResponse.contains("\"results\":[]") || !jsonResponse.contains("\"latitude\"")) {
            throw new RuntimeException("City not found: " + cityName);
        }
        
        double latitude = extractDoubleValue(jsonResponse, "latitude");
        double longitude = extractDoubleValue(jsonResponse, "longitude");
        
        return new double[]{latitude, longitude};
    }

    /**
     * Extract a double value from JSON string
     */
    private double extractDoubleValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            throw new RuntimeException("Failed to parse " + key + " from response");
        }
        startIndex += searchKey.length();
        int endIndex = json.indexOf(",", startIndex);
        if (endIndex == -1) {
            endIndex = json.indexOf("}", startIndex);
        }
        String value = json.substring(startIndex, endIndex).trim();
        return Double.parseDouble(value);
    }

    /**
     * Get weather code description
     */
    private String getWeatherDescription(int weatherCode) {
        return switch (weatherCode) {
            case 0 -> "Clear sky";
            case 1, 2, 3 -> "Mainly clear to partly cloudy";
            case 45, 48 -> "Foggy";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snow fall";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown weather";
        };
    }

    @Bean
    public io.modelcontextprotocol.server.McpSyncServer mcpSyncServer(WebMvcSseServerTransportProvider transportProvider) {
        
        // Define tools
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        
        // Tool 1: Weather lookup (using Open-Meteo API - free, no auth required)
        McpSchema.JsonSchema weatherSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "city", Map.of("type", "string", "description", "City name"),
                        "country", Map.of("type", "string", "description", "Country code (optional, helps with disambiguation)")
                ),
                List.of("city"),
                null,
                null,
                null
        );
        McpSchema.Tool weatherTool = McpSchema.Tool.builder()
                .name("get_weather")
                .description("Get current weather information for a city using real-time data from Open-Meteo API")
                .inputSchema(weatherSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(weatherTool, (exchange, request) -> {
            try {
                String city = (String) request.arguments().get("city");
                
                // Step 1: Geocode city to coordinates
                double[] coords = geocodeCity(city);
                double latitude = coords[0];
                double longitude = coords[1];
                
                // Step 2: Get weather data from Open-Meteo
                String weatherUrl = String.format(
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&temperature_unit=celsius",
                    latitude, longitude
                );
                
                URL url = new URL(weatherUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                
                String jsonResponse = response.toString();
                
                // Parse weather data from the "current" section (not "current_units")
                // Extract the "current" object first
                int currentStart = jsonResponse.indexOf("\"current\":{");
                if (currentStart == -1) {
                    throw new RuntimeException("Invalid weather API response - missing 'current' section");
                }
                String currentSection = jsonResponse.substring(currentStart);
                // Find the end of the current object (next top-level })
                int currentEnd = currentSection.indexOf("}}");
                if (currentEnd == -1) {
                    currentEnd = currentSection.length();
                }
                currentSection = currentSection.substring(0, currentEnd + 1);
                
                // Parse weather data from current section
                double temperature = extractDoubleValue(currentSection, "temperature_2m");
                int humidity = (int) extractDoubleValue(currentSection, "relative_humidity_2m");
                int weatherCode = (int) extractDoubleValue(currentSection, "weather_code");
                double windSpeed = extractDoubleValue(currentSection, "wind_speed_10m");
                String weatherDesc = getWeatherDescription(weatherCode);
                
                String weatherInfo = String.format(
                    "Weather in %s:\n" +
                    "  Condition: %s (Code: %d)\n" +
                    "  Temperature: %.1f°C (%.1f°F)\n" +
                    "  Humidity: %d%%\n" +
                    "  Wind Speed: %.1f km/h",
                    city,
                    weatherDesc,
                    weatherCode,
                    temperature,
                    temperature * 9.0 / 5.0 + 32.0,
                    humidity,
                    windSpeed
                );
                
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(weatherInfo)),
                        false,
                        null,
                        null
                );
            } catch (Exception e) {
                String errorMsg = "Failed to get weather data: " + e.getMessage();
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(errorMsg)),
                        true,
                        null,
                        null
                );
            }
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
