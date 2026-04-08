# MCP Test Server

A test MCP (Model Context Protocol) server for development and testing with the aichat project.

## Features

This server provides the following sample resources:

### Tools
- **get_weather** - Get current weather information for a city (using real-time data from Open-Meteo API)
  - Parameters: `city` (required), `country` (optional)
  - Returns: Real-time temperature, humidity, weather conditions, and wind speed
  
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

## How to Use the Weather Tool

The weather tool now fetches **real-time data** from the Open-Meteo API. When you ask about weather in the aichat web client, it will:

1. Automatically detect your weather request
2. Extract the city name from your input
3. Call the MCP tool to fetch real data
4. Present the actual weather information to you

### Example Queries That Trigger the Weather Tool:

- "What's the weather in Moscow?"
- "Tell me the weather in London"
- "How's the weather in Tokyo today?"
- "What's the temperature in Paris?"
- "Погода в Москве" (Russian also works!)

### Expected Response Flow:

When you ask about weather, the system will:
1. Fetch real-time data (temperature, humidity, wind, conditions)
2. Show you a plan incorporating the actual weather data
3. After approval, execute and present the real weather information

The LLM will receive the actual data and should present it naturally instead of using placeholders.

### Option 1: Use the Automated Script (Recommended)

A convenience script is provided to start both servers and verify they're working:

```bash
cd /Users/paveltitov/Documents/programming/ai_challenge
./run_servers.sh
```

This script will:
1. Build both projects
2. Start the MCP Test Server on port 8081
3. Start the AIChat application on port 8080
4. Display available MCP commands

### Option 2: Manual Startup

#### 1. Start the MCP Test Server

```bash
cd ../mcp-test-server
mvn spring-boot:run
```

The server will start on `http://localhost:8081`

#### 2. Start the AIChat Application

In a separate terminal:

```bash
cd ../aichat
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### 3. Test the Connection

Once both servers are running, open your browser to `http://localhost:8080` and use the following commands in the chat interface:

- `/mcp_connect` - Establish connection to the MCP server
- `/mcp_status` - Check connection status
- `/mcp_list` - List all available tools, resources, and prompts

## Expected Output

When you run `/mcp_list` after connecting, you should see:

```
Available MCP Servers and Resources:
  Server: http://localhost:8081
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
- **Spring Boot 3.2.0** - Application framework
- **MCP SDK 1.1.1** - Model Context Protocol implementation
- **WebMvc SSE Transport** - Server-Sent Events for real-time communication
- **Open-Meteo API** - Free, open-source weather data (no API key required)

The client (aichat) connects via SSE (Server-Sent Events) to establish a bidirectional communication channel.

### Weather Data Source

The weather tool uses two free APIs from Open-Meteo:
1. **Geocoding API** - Converts city names to coordinates
2. **Weather Forecast API** - Provides real-time weather data

No API key or authentication is required - see https://open-meteo.com/ for details.
