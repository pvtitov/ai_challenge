package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.constants.ApiConstants;
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

    public GigaChatApiService() {
        this.apiCredentials = System.getenv(ApiConstants.GIGACHAT_API_CREDENTIALS_ENV);
        if (apiCredentials == null || apiCredentials.isEmpty()) {
            this.apiCredentials = "placeholder";
        }
    }

    public String getCompletion(JSONArray messages) throws IOException {
        OkHttpClient client = getUnsafeOkHttpClientBuilder().build();
        MediaType mediaType = MediaType.parse("application/json");

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "GigaChat:latest");
        requestBody.put("temperature", 0.7);
        requestBody.put("n", 1);
        requestBody.put("max_tokens", 512);
        requestBody.put("messages", messages);

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
            return jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
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
