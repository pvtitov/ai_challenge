package com.github.pvtitov.aichatgithub.service;

import com.github.pvtitov.aichatgithub.constants.ApiConstants;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * LLM service implementation for local Ollama models.
 * Supports models like llama3.2:1b running on localhost:11434.
 */
public class OllamaLlmService implements LlmService {

    private static final Logger logger = LoggerFactory.getLogger(OllamaLlmService.class);

    private final LlmModel model;
    private final OkHttpClient httpClient;
    private final String ollamaUrl;
    private final String modelName;

    public OllamaLlmService(String modelName) {
        this.modelName = modelName;
        this.model = new LlmModel(
            "ollama-" + modelName.replaceAll("[^a-zA-Z0-9]", "-"),
            "Ollama: " + modelName,
            LlmModel.LlmProvider.OLLAMA,
            modelName
        );
        this.ollamaUrl = ApiConstants.OLLAMA_URL;
        this.httpClient = new OkHttpClient.Builder().build();
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public LlmModel getModel() {
        return model;
    }

    @Override
    public LlmResponse callChatApi(List<LlmMessage> messages, String systemPrompt) {
        return callChat(messages, systemPrompt);
    }

    @Override
    public LlmResponse callTaskDecisionApi(String systemPrompt, List<LlmMessage> messages) {
        return callChat(messages, systemPrompt);
    }

    @Override
    public LlmResponse callTaskCompletionApi(String systemPrompt, List<LlmMessage> messages) {
        return callChat(messages, systemPrompt);
    }

    private LlmResponse callChat(List<LlmMessage> messages, String systemPrompt) {
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
            requestBody.put("model", model.getModelName());
            requestBody.put("messages", requestMessages);
            requestBody.put("stream", false);

            RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(ollamaUrl + "/api/chat")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Ollama API request failed: " + response.code() + " - " + 
                        (response.body() != null ? response.body().string() : "no response body"));
                }

                String responseBody = response.body().string();
                logger.debug("Ollama response: {}", responseBody);

                return parseResponse(responseBody);
            }
        } catch (IOException | JSONException e) {
            logger.error("Failed to call Ollama API", e);
            throw new RuntimeException("Failed to call Ollama API", e);
        }
    }

    private LlmResponse parseResponse(String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            JSONObject message = jsonResponse.getJSONObject("message");
            String content = message.getString("content");

            // Ollama doesn't always provide token counts in chat endpoint
            // Some versions provide usage in prompt_eval_count + eval_count
            int promptTokens = jsonResponse.optInt("prompt_eval_count", 0);
            int completionTokens = jsonResponse.optInt("eval_count", 0);
            int totalTokens = promptTokens + completionTokens;

            return new LlmResponse(content, promptTokens, completionTokens, totalTokens);
        } catch (JSONException e) {
            logger.error("Failed to parse Ollama response: {}", responseBody, e);
            throw new RuntimeException("Failed to parse Ollama response", e);
        }
    }
}
