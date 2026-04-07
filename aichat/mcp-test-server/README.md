# MCP Test Server

A test MCP (Model Context Protocol) server for development and testing with the aichat project.

## Features

This server provides the following sample resources:

### Tools
- **get_weather** - Get current weather information for a city
  - Parameters: `city` (required), `country` (optional)
  
- **calculate** - Perform mathematical calculations
  - Parameters: `expression` (required)
  
- **get_current_time** - Get the current date and time
  - Parameters: `timezone` (optional)

### Resources
- **file:///docs/readme.md** - Project README documentation
- **file:///config/settings.json** - Configuration settings

### Prompts
- **summarize_text** - Summarize text into key points
  - Parameters: `text` (required), `max_length` (optional)
  
- **code_review** - Review code for best practices
  - Parameters: `code` (required), `language` (optional)

## How to Run

### 1. Start the MCP Test Server

```bash
cd mcp-test-server
mvn spring-boot:run
```

The server will start on `http://localhost:8081`

### 2. Start the aichat Application

In a separate terminal:

```bash
cd aichat
mvn spring-boot:run
```

### 3. Test the Connection

Once both servers are running, use the following commands in the aichat web interface:

- `/mcp_connect` - Establish connection to the MCP server
- `/mcp_status` - Check connection status
- `/list_mcp` - List all available tools, resources, and prompts

## Expected Output

When you run `/list_mcp` after connecting, you should see:

```
Available MCP Servers and Resources:
  Server: http://localhost:8081/mcp
  Tools:
    - get_weather: Get current weather information for a city
    - calculate: Perform mathematical calculations
    - get_current_time: Get the current date and time
  Resources:
    - file:///docs/readme.md (README Documentation)
    - file:///config/settings.json (Configuration Settings)
  Prompts:
    - summarize_text: Summarize a given text into key points
    - code_review: Review code for best practices and potential issues
```

## Architecture

The MCP server uses:
- **Spring Boot 2.7.0** - Application framework
- **MCP SDK 1.1.1** - Model Context Protocol implementation
- **WebMvc SSE Transport** - Server-Sent Events for real-time communication

The client (aichat) connects via SSE (Server-Sent Events) to establish a bidirectional communication channel.
