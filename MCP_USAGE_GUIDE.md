# Using MCP Server with AIChat Web Client

This guide explains how to use the MCP (Model Context Protocol) test server with the aichat web client.

## Overview

The MCP test server provides additional tools, resources, and prompts that can be accessed through the aichat web interface. This allows the AI to use external tools like weather lookup, calculations, and time queries.

## Quick Start

### 1. Start Both Servers

**Option A: Using the automated script (Recommended)**
```bash
cd /Users/paveltitov/Documents/programming/ai_challenge
./run_servers.sh
```

**Option B: Manual startup**

Terminal 1 - MCP Test Server:
```bash
cd /Users/paveltitov/Documents/programming/ai_challenge/mcp-test-server
mvn spring-boot:run
```

Terminal 2 - AIChat Application:
```bash
cd /Users/paveltitov/Documents/programming/ai_challenge/aichat
mvn spring-boot:run
```

### 2. Open the Web Client

Open your browser and navigate to: **http://localhost:8080**

### 3. Connect to the MCP Server

In the chat input field, type the following command and press Enter:

```
/mcp_connect
```

You should see: `MCP connection established successfully.`

### 4. Verify the Connection

Check the connection status:

```
/mcp_status
```

You should see: `ACTIVE` or similar status indicating the connection is working.

### 5. List Available Tools

View all available MCP tools, resources, and prompts:

```
/mcp_list
```

This will display:
- **Tools**: get_weather, calculate, get_current_time
- **Resources**: Documentation and configuration files
- **Prompts**: summarize_text, code_review

## Available MCP Tools

Once connected, you can ask the AI to use these tools in your conversations:

### 1. Weather Tool (get_weather)

Get current weather information for any city.

**Example prompts:**
- "What's the weather in London?"
- "Get the weather for Tokyo, Japan"
- "Tell me the current weather in Paris"

### 2. Calculator Tool (calculate)

Perform mathematical calculations.

**Example prompts:**
- "Calculate 245 * 37.5"
- "What is 1024 / 8?"
- "Calculate: (15 + 27) * 3"

### 3. Time Tool (get_current_time)

Get the current date and time.

**Example prompts:**
- "What time is it now?"
- "Get the current time"
- "What's the current date and time?"

## Available MCP Prompts

Prompts are predefined templates that help structure conversations.

### 1. Summarize Text (summarize_text)

Summarize text into key points.

**Example usage:**
Just ask the AI to summarize text, and it may use this prompt:
- "Summarize this article: [paste your text here]"
- "Give me the key points of: [your text]"

### 2. Code Review (code_review)

Review code for best practices and potential issues.

**Example usage:**
- "Review this Python code: [paste your code]"
- "Check this code for best practices: [your code]"

## Available MCP Resources

Resources are files and documents that the AI can reference.

- **file:///docs/readme.md** - Project README documentation
- **file:///config/settings.json** - Configuration settings

## Troubleshooting

### Connection Issues

If `/mcp_connect` fails:
1. Verify the MCP test server is running on port 8081
2. Check that the aichat server is running on port 8080
3. Review the terminal output for error messages
4. Ensure the configuration in `aichat/src/main/resources/application.properties` has:
   ```
   mcp.server.url=http://localhost:8081
   ```

### Tools Not Appearing

If `/mcp_list` shows no tools:
1. Make sure you ran `/mcp_connect` first
2. Check the MCP test server logs for any errors
3. Try restarting both servers

### Server Won't Start

If either server fails to start:
1. Check if ports 8080 or 8081 are already in use:
   ```bash
   lsof -i :8080
   lsof -i :8081
   ```
2. Kill any existing processes:
   ```bash
   kill -9 <PID>
   ```
3. Rebuild the projects:
   ```bash
   cd mcp-test-server && mvn clean package
   cd ../aichat && mvn clean package
   ```

## Stopping the Servers

When you're done:
- If using the `run_servers.sh` script: Press `Ctrl+C` in the terminal
- If running manually: Press `Ctrl+C` in each terminal window

## Configuration

The MCP server URL is configured in:
`aichat/src/main/resources/application.properties`

Default configuration:
```properties
mcp.server.url=http://localhost:8081
```

To connect to a different MCP server, change this URL and restart the aichat application.

## Architecture

```
┌─────────────┐         ┌──────────────────┐
│   Browser   │◄───────►│   AIChat Server  │
│  (Web UI)   │  HTTP   │   (Port 8080)    │
└─────────────┘         └────────┬─────────┘
                                 │
                            SSE  │
                                 │
                        ┌────────▼─────────┐
                        │  MCP Test Server │
                        │   (Port 8081)    │
                        └──────────────────┘
```

- The web client communicates with the aichat server via HTTP
- The aichat server connects to the MCP test server using Server-Sent Events (SSE)
- MCP tools, resources, and prompts are made available to the AI during conversations
