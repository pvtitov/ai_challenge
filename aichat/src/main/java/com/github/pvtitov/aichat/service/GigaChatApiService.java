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
        // The prompt now directly comes from the service layer
        String prompt = userInput;

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        // The messages array from history is now prepended to the request
        JSONArray requestMessages = new JSONArray();
        for (int i = 0; i < messages.length(); i++) {
            requestMessages.put(messages.get(i));
        }
        requestMessages.put(userMessage);

        return callGigaChat(requestMessages);
    }

    private GigaChatComplexResponse callGigaChat(JSONArray requestMessages) throws IOException {
        OkHttpClient client = getUnsafeOkHttpClientBuilder().build();
        MediaType mediaType = MediaType.parse("application/json");

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "GigaChat:latest");
        requestBody.put("temperature", 0.7);
        requestBody.put("n", 1);
        requestBody.put("max_tokens", 2048);
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

            Integer promptTokens = jsonResponse.has("usage") ? jsonResponse.getJSONObject("usage").optInt("prompt_tokens", 0) : 0;
            Integer completionTokens = jsonResponse.has("usage") ? jsonResponse.getJSONObject("usage").optInt("completion_tokens", 0) : 0;
            Integer totalTokens = jsonResponse.has("usage") ? jsonResponse.getJSONObject("usage").optInt("total_tokens", 0) : 0;

            // Try to parse as complex response, otherwise return as plain text
            try {
                String jsonPart = extractJson(content);
                GigaChatComplexResponse complexResponse = objectMapper.readValue(jsonPart, GigaChatComplexResponse.class);
                complexResponse.setPromptTokens(promptTokens);
                complexResponse.setCompletionTokens(completionTokens);
                complexResponse.setTotalTokens(totalTokens);
                return complexResponse;
            } catch (Exception e) {
                GigaChatComplexResponse complexResponse = new GigaChatComplexResponse();
                complexResponse.setFullResponse(content); // Return raw content if not a JSON object
                complexResponse.setSummary("");
                complexResponse.setStickyFacts("");
                complexResponse.setPromptTokens(promptTokens);
                complexResponse.setCompletionTokens(completionTokens);
                complexResponse.setTotalTokens(totalTokens);
                return complexResponse;
            }
        }
    }

    private String extractJson(String rawString) {
        int startIndex = rawString.indexOf('{');
        int endIndex = rawString.lastIndexOf('}');
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return rawString.substring(startIndex, endIndex + 1);
        }
        return rawString; // Return original if no JSON object is found
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
