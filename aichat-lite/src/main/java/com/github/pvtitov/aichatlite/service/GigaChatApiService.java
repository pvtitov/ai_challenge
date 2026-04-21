package com.github.pvtitov.aichatlite.service;

import com.github.pvtitov.aichatlite.constants.ApiConstants;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;

public class GigaChatApiService implements LlmService {

    private static final Logger logger = LoggerFactory.getLogger(GigaChatApiService.class);

    private static final LlmModel GIGACHAT_MODEL = new LlmModel(
        "gigachat",
        "GigaChat",
        LlmModel.LlmProvider.GIGACHAT,
        "GigaChat:latest"
    );

    private String accessToken;
    private long tokenExpiryTime;
    private final String apiCredentials;
    private final OkHttpClient httpClient;

    public GigaChatApiService() {
        this.apiCredentials = System.getenv(ApiConstants.GIGACHAT_API_CREDENTIALS_ENV);
        if (this.apiCredentials == null || this.apiCredentials.isEmpty()) {
            throw new IllegalStateException("Environment variable " + ApiConstants.GIGACHAT_API_CREDENTIALS_ENV + " is not set");
        }
        this.httpClient = getUnsafeOkHttpClientBuilder().build();
    }

    @Override
    public LlmModel getModel() {
        return GIGACHAT_MODEL;
    }

    @Override
    public LlmResponse callChatApi(List<LlmMessage> messages, String systemPrompt) {
        return callApi(messages, systemPrompt);
    }

    @Override
    public LlmResponse callTaskDecisionApi(String systemPrompt, List<LlmMessage> messages) {
        return callApi(messages, systemPrompt);
    }

    @Override
    public LlmResponse callTaskCompletionApi(String systemPrompt, List<LlmMessage> messages) {
        return callApi(messages, systemPrompt);
    }

    private LlmResponse callApi(List<LlmMessage> messages, String systemPrompt) {
        try {
            JSONArray requestMessages = new JSONArray();

            // Add system prompt
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            requestMessages.put(systemMsg);

            // Add conversation history
            for (LlmMessage msg : messages) {
                JSONObject jsonMsg = new JSONObject();
                jsonMsg.put("role", msg.getRole());
                jsonMsg.put("content", msg.getContent());
                requestMessages.put(jsonMsg);
            }

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", GIGACHAT_MODEL.getModelName());
            requestBody.put("temperature", 0.7);
            requestBody.put("n", 1);
            requestBody.put("messages", requestMessages);

            RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(ApiConstants.GIGA_CHAT_API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + getAccessToken())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("GigaChat API request failed: " + response.code());
                }

                String responseBody = response.body().string();
                logger.debug("GigaChat response: {}", responseBody);

                return parseResponse(responseBody);
            }
        } catch (IOException | JSONException e) {
            logger.error("Failed to call GigaChat API", e);
            throw new RuntimeException("Failed to call GigaChat API", e);
        }
    }

    private LlmResponse parseResponse(String responseBody) {
        JSONObject jsonResponse = new JSONObject(responseBody);
        JSONArray choices = jsonResponse.getJSONArray("choices");

        if (choices.length() == 0) {
            throw new RuntimeException("No choices in GigaChat response");
        }

        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject message = firstChoice.getJSONObject("message");
        String content = message.getString("content");

        // Extract token usage
        JSONObject usage = jsonResponse.optJSONObject("usage");
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;

        if (usage != null) {
            promptTokens = usage.optInt("prompt_tokens", 0);
            completionTokens = usage.optInt("completion_tokens", 0);
            totalTokens = usage.optInt("total_tokens", 0);
        }

        return new LlmResponse(content, promptTokens, completionTokens, totalTokens);
    }

    private synchronized void fetchNewAccessToken() throws IOException {
        RequestBody body = RequestBody.create("scope=GIGACHAT_API_PERS", MediaType.parse("application/x-www-form-urlencoded"));
        Request request = new Request.Builder()
                .url(ApiConstants.GIGA_CHAT_AUTH_URL)
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Accept", "application/json")
                .addHeader("RqUID", UUID.randomUUID().toString())
                .addHeader("Authorization", "Basic " + apiCredentials)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Authentication failed: " + response.code());
            }

            String responseBody = response.body().string();
            JSONObject json = new JSONObject(responseBody);
            this.accessToken = json.getString("access_token");
            this.tokenExpiryTime = System.currentTimeMillis() + ApiConstants.TOKEN_EXPIRATION_MS;
            logger.info("Successfully obtained GigaChat access token");
        }
    }

    private String getAccessToken() throws IOException {
        if (this.accessToken == null || System.currentTimeMillis() >= this.tokenExpiryTime) {
            fetchNewAccessToken();
        }
        return this.accessToken;
    }

    private static OkHttpClient.Builder getUnsafeOkHttpClientBuilder() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
                }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            return builder;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL client", e);
        }
    }
}
