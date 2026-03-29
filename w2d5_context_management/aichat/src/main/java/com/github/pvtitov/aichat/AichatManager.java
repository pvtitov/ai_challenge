package com.github.pvtitov.aichat;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AichatManager {

    private static final String DB_URL = "jdbc:sqlite:aichat.db";
    private static final String GIGA_CHAT_AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    private static final String GIGA_CHAT_API_URL = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions";
    private static final long TOKEN_EXPIRATION_MS = TimeUnit.MINUTES.toMillis(30);

    private final String apiCredentials;
    private String accessToken;
    private long tokenExpiryTime;

    private java.sql.Connection connection;

    private HistoryStrategy historyStrategy = new UnlimitedHistoryStrategy();
    private int currentBranch = 1;

    public AichatManager() {
        this.apiCredentials = System.getenv("GIGACHAT_API_CREDENTIALS");
        if (apiCredentials == null || apiCredentials.isEmpty()) {
            throw new IllegalStateException("GIGACHAT_API_CREDENTIALS system environment variable not set");
        }
        try {
            connection = DriverManager.getConnection(DB_URL);
            initDb();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to database", e);
        }
    }

    private void initDb() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS chat_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "branch INTEGER NOT NULL," +
                    "role TEXT NOT NULL," +
                    "content TEXT NOT NULL," +
                    "prompt_tokens INTEGER," +
                    "completion_tokens INTEGER," +
                    "total_tokens INTEGER," +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    private synchronized void fetchNewAccessToken() throws IOException {
        OkHttpClient client = getUnsafeOkHttpClientBuilder().build();
        RequestBody body = RequestBody.create("scope=GIGACHAT_API_PERS", MediaType.parse("application/x-www-form-urlencoded"));
        Request request = new Request.Builder()
                .url(GIGA_CHAT_AUTH_URL)
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Accept", "application/json")
                .addHeader("RqUID", UUID.randomUUID().toString())
                .addHeader("Authorization", "Basic " + apiCredentials)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to obtain access token. Response: " + response.body().string());
            }
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            this.accessToken = jsonResponse.getString("access_token");
            this.tokenExpiryTime = System.currentTimeMillis() + TOKEN_EXPIRATION_MS;
        }
    }

    private String getAccessToken() throws IOException {
        if (this.accessToken == null || System.currentTimeMillis() >= this.tokenExpiryTime) {
            fetchNewAccessToken();
        }
        return this.accessToken;
    }

    public ChatResponse process(String userInput) throws IOException {
        if (userInput.startsWith("/")) {
            String commandResult = handleCommand(userInput);
            // Returning command output as part of the ChatResponse
            return new ChatResponse(commandResult, 0, 0, 0, getCumulativeTokens());
        }

        saveMessage("user", userInput, currentBranch, null, null, null);

        OkHttpClient client = getUnsafeOkHttpClientBuilder().build();
        MediaType mediaType = MediaType.parse("application/json");

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "GigaChat:latest");
        requestBody.put("temperature", 0.7);
        requestBody.put("n", 1);
        requestBody.put("max_tokens", 512);

        JSONArray messages = historyStrategy.getHistory(this);
        requestBody.put("messages", messages);

        RequestBody body = RequestBody.create(requestBody.toString(), mediaType);
        Request request = new Request.Builder()
                .url(GIGA_CHAT_API_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + getAccessToken())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API call failed: " + response.body().string());
            }

            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            String assistantMessage = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");

            JSONObject usage = jsonResponse.getJSONObject("usage");
            int promptTokens = usage.getInt("prompt_tokens");
            int completionTokens = usage.getInt("completion_tokens");
            int totalTokens = usage.getInt("total_tokens");

            saveMessage("assistant", assistantMessage, currentBranch, promptTokens, completionTokens, totalTokens);

            long cumulativeTokens = getCumulativeTokens();

            return new ChatResponse(assistantMessage, promptTokens, completionTokens, totalTokens, cumulativeTokens);
        }
    }

    private long getCumulativeTokens() {
        try (PreparedStatement statement = connection.prepareStatement("SELECT SUM(total_tokens) FROM chat_history WHERE branch = ?")) {
            statement.setInt(1, currentBranch);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            // Log this exception properly in a real application
            e.printStackTrace();
        }
        return 0;
    }

    private static OkHttpClient.Builder getUnsafeOkHttpClientBuilder() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            return builder;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private String handleCommand(String command) {
        String[] parts = command.split(" ");
        String cmd = parts[0];

        switch (cmd) {
            case "/clean":
                return cleanHistory();
            case "/branch":
                return createBranch();
            case "/switch":
                if (parts.length > 1) {
                    return switchBranch(Integer.parseInt(parts[1]));
                } else {
                    return switchBranch();
                }
            case "/history":
                return getHistoryAsString();
            case "/strategy":
                if (parts.length > 1) {
                    return setHistoryStrategy(parts[1], parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
                } else {
                    return "Usage: /strategy [unlimited|sliding|sticky] [size]";
                }
            default:
                return "Unknown command: " + cmd;
        }
    }

    private String cleanHistory() {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM chat_history WHERE branch = ?")) {
            statement.setInt(1, currentBranch);
            statement.executeUpdate();
            return "History for branch " + currentBranch + " cleared.";
        } catch (SQLException e) {
            return "Error clearing history: " + e.getMessage();
        }
    }

    private String createBranch() {
        try {
            // Get history from the current strategy
            JSONArray history = historyStrategy.getHistory(this);

            // Determine the new branch number
            int newBranch;
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT MAX(branch) FROM chat_history")) {
                newBranch = rs.getInt(1) + 1;
            }

            // Save the history to the new branch
            for (int i = 0; i < history.length(); i++) {
                JSONObject message = history.getJSONObject(i);
                // We can't save tokens here as we don't have them from getHistory
                saveMessage(message.getString("role"), message.getString("content"), newBranch, null, null, null);
            }

            int previousBranch = currentBranch;
            currentBranch = newBranch;
            return "Switched to new branch " + currentBranch + ", inheriting history from branch " + previousBranch + " based on the current strategy.";

        } catch (SQLException e) {
            return "Error creating branch: " + e.getMessage();
        }
    }

    private String switchBranch(int branch) {
        this.currentBranch = branch;
        return "Switched to branch " + branch;
    }

    private String switchBranch() {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT DISTINCT branch FROM chat_history ORDER BY branch")) {
            List<Integer> branches = new ArrayList<>();
            while (rs.next()) {
                branches.add(rs.getInt("branch"));
            }
            if (branches.isEmpty()) {
                return "No branches available to switch to.";
            }
            int currentIndex = branches.indexOf(currentBranch);
            if (currentIndex == -1 || currentIndex + 1 >= branches.size()) {
                currentBranch = branches.get(0);
            } else {
                currentBranch = branches.get(currentIndex + 1);
            }
            return "Switched to branch " + currentBranch;
        } catch (SQLException e) {
            return "Error switching branch: " + e.getMessage();
        }
    }

    private String getHistoryAsString() {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement statement = connection.prepareStatement("SELECT role, content FROM chat_history WHERE branch = ? ORDER BY timestamp")) {
            statement.setInt(1, currentBranch);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                sb.append(rs.getString("role")).append(": ").append(rs.getString("content")).append("\n");
            }
        }
        catch (SQLException e) {
            return "Error getting history: " + e.getMessage();
        }
        return sb.toString();
    }

    private String setHistoryStrategy(String strategy, int size) {
        switch (strategy) {
            case "unlimited":
                this.historyStrategy = new UnlimitedHistoryStrategy();
                return "History strategy set to unlimited.";
            case "sliding":
                this.historyStrategy = new SlidingWindowHistoryStrategy(size > 0 ? size : 5);
                return "History strategy set to sliding window with size " + (size > 0 ? size : 5) + ".";
            case "sticky":
                this.historyStrategy = new StickyFactsHistoryStrategy();
                return "History strategy set to sticky facts.";
            default:
                return "Unknown strategy: " + strategy;
        }
    }

    void saveMessage(String role, String content, int branch, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO chat_history (branch, role, content, prompt_tokens, completion_tokens, total_tokens) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, branch);
            statement.setString(2, role);
            statement.setString(3, content);
            if (promptTokens != null) {
                statement.setInt(4, promptTokens);
                statement.setInt(5, completionTokens);
                statement.setInt(6, totalTokens);
            } else {
                statement.setNull(4, Types.INTEGER);
                statement.setNull(5, Types.INTEGER);
                statement.setNull(6, Types.INTEGER);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save message", e);
        }
    }

    public int getCurrentBranch() {
        return currentBranch;
    }

    public java.sql.Connection getConnection() {
        return connection;
    }

    interface HistoryStrategy {
        JSONArray getHistory(AichatManager manager);
    }

    static class UnlimitedHistoryStrategy implements HistoryStrategy {
        @Override
        public JSONArray getHistory(AichatManager manager) {
            JSONArray messages = new JSONArray();
            try (PreparedStatement statement = manager.getConnection().prepareStatement(
                    "SELECT role, content FROM chat_history WHERE branch = ? ORDER BY timestamp")) {
                statement.setInt(1, manager.getCurrentBranch());
                ResultSet rs = statement.executeQuery();
                while (rs.next()) {
                    JSONObject message = new JSONObject();
                    message.put("role", rs.getString("role"));
                    message.put("content", rs.getString("content"));
                    messages.put(message);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get history", e);
            }
            return messages;
        }
    }

    static class SlidingWindowHistoryStrategy implements HistoryStrategy {
        private final int windowSize;

        public SlidingWindowHistoryStrategy(int windowSize) {
            this.windowSize = windowSize;
        }

        @Override
        public JSONArray getHistory(AichatManager manager) {
            JSONArray messages = new JSONArray();
            try (PreparedStatement statement = manager.getConnection().prepareStatement(
                    "SELECT role, content FROM chat_history WHERE branch = ? ORDER BY timestamp DESC LIMIT ?")) {
                statement.setInt(1, manager.getCurrentBranch());
                statement.setInt(2, windowSize * 2); // user and assistant messages
                ResultSet rs = statement.executeQuery();
                List<JSONObject> history = new ArrayList<>();
                while (rs.next()) {
                    JSONObject message = new JSONObject();
                    message.put("role", rs.getString("role"));
                    message.put("content", rs.getString("content"));
                    history.add(0, message);
                }
                messages = new JSONArray(history);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get history", e);
            }
            return messages;
        }
    }

    static class StickyFactsHistoryStrategy implements HistoryStrategy {
        @Override
        public JSONArray getHistory(AichatManager manager) {
            // This is a simplified version. A real implementation would require NLP to extract facts.
            // For now, it just returns the last message.
            JSONArray messages = new JSONArray();
            try (PreparedStatement statement = manager.getConnection().prepareStatement(
                    "SELECT role, content FROM chat_history WHERE branch = ? AND role = 'user' ORDER BY timestamp DESC LIMIT 1")) {
                statement.setInt(1, manager.getCurrentBranch());
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    JSONObject message = new JSONObject();
                    message.put("role", "system");
                    message.put("content", "Remember this fact: " + rs.getString("content"));
                    messages.put(message);
                }
            }
            catch (SQLException e) {
                throw new RuntimeException("Failed to get history", e);
            }
            return messages;
        }
    }
}
