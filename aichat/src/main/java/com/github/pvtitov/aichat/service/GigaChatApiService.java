package com.github.pvtitov.aichat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pvtitov.aichat.constants.ApiConstants;
import com.github.pvtitov.aichat.dto.GigaChatComplexResponse;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.UUID;

@Service
public class GigaChatApiService {

    private String apiCredentials;
    private String accessToken;
    private long tokenExpiryTime;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GigaChatApiService() {
        this.apiCredentials = System.getenv(ApiConstants.GIGACHAT_API_CREDENTIALS_ENV);
        if (apiCredentials == null || apiCredentials.isEmpty()) {
            this.apiCredentials = "placeholder";
        }
    }

    public GigaChatComplexResponse getCompletion(JSONArray messages, String userInput) throws IOException {
        OkHttpClient client = getUnsafeOkHttpClientBuilder().build();
        MediaType mediaType = MediaType.parse("application/json");

        String prompt = "Based on the following conversation history:\n" +
                messages.toString() + "\n\n" +
                "And the user's latest message:\n" +
                "\"" + userInput + "\"\n\n" +
                "Please provide a JSON response with three fields:\n" +
                "1. \"full_response\": A detailed, complete answer to the user's message.\n" +
                "2. \"summary\": A concise, one-sentence summary of your full response.\n" +
                "3. \"sticky_facts\": A list of key facts, names, or important pieces of information from your response that should be remembered for future interactions.";

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        JSONArray requestMessages = new JSONArray();
        requestMessages.put(userMessage);

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "GigaChat:latest");
        requestBody.put("temperature", 0.7);
        requestBody.put("n", 1);
        requestBody.put("max_tokens", 2048); // Increased max_tokens for complex response
        requestBody.put("messages", requestMessages);

        RequestBody body = RequestBody.create(requestBody.toString(), mediaType);
        Request request = new Request.Builder()
                .url(ApiConstants.GIGA_CHAT_API_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + getAccessToken())
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("API call failed: " + responseBody);
            }

            JSONObject jsonResponse = new JSONObject(responseBody);
            String content = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");

            // Clean the content to be valid JSON
            content = extractJson(content);

            try {
                GigaChatComplexResponse complexResponse = objectMapper.readValue(content, GigaChatComplexResponse.class);
                return complexResponse;
            } catch (Exception e) {
                // If parsing fails, return the raw content in the full_response and empty strings for others
                GigaChatComplexResponse complexResponse = new GigaChatComplexResponse();
                complexResponse.setFullResponse(content);
                complexResponse.setSummary("");
                complexResponse.setStickyFacts("");
                return complexResponse;
            }
        }
    }

    private String extractJson(String rawString) {
        StringBuilder result = new StringBuilder();
        boolean isStartedJson = false;
        boolean isEndedJson = false;
        char currentChar;
        for (int i = 0; i < rawString.length(); i++) {
            currentChar = rawString.charAt(i);
            if (!isStartedJson) {
                isStartedJson = currentChar == '{';
            }
            if (isStartedJson) {
                result.append(currentChar);
            }
        }
        for (int i = result.length() - 1; i >= 0 ; i--) {
            if (!isEndedJson) {
                isEndedJson = rawString.charAt(i) == '}';
            }
            if (!isStartedJson) {
                result.deleteCharAt(i);
            }
        }
        if (isStartedJson && isEndedJson) {
            return result.toString();
        } else {
            return rawString;
        }
    }

    private synchronized void fetchNewAccessToken() throws IOException {
        OkHttpClient client = getUnsafeOkHttpClientBuilder().build();
        RequestBody body = RequestBody.create("scope=GIGACHAT_API_PERS", MediaType.parse("application/x-www-form-urlencoded"));
        Request request = new Request.Builder()
                .url(ApiConstants.GIGA_CHAT_AUTH_URL)
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Accept", "application/json")
                .addHeader("RqUID", UUID.randomUUID().toString())
                .addHeader("Authorization", "Basic " + apiCredentials)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Failed to obtain access token. Response: " + responseBody);
            }
            JSONObject jsonResponse = new JSONObject(responseBody);
            this.accessToken = jsonResponse.getString("access_token");
            this.tokenExpiryTime = System.currentTimeMillis() + ApiConstants.TOKEN_EXPIRATION_MS;
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
}