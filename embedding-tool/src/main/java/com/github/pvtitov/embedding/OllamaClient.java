package com.github.pvtitov.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Client for Ollama's embedding API.
 */
public class OllamaClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "nomic-embed-text";

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaClient() {
        this(DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    public OllamaClient(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generates an embedding for the given text using Ollama.
     *
     * @param text input text
     * @return embedding vector as float array
     */
    public float[] embed(String text) {
        try {
            String requestBody = objectMapper.writeValueAsString(
                    new EmbedRequest(model, text)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embeddings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Ollama API error (status " + response.statusCode() + "): " + response.body()
                );
            }

            JsonNode jsonNode = objectMapper.readTree(response.body());
            JsonNode embeddingNode = jsonNode.get("embedding");

            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new RuntimeException("Invalid embedding response from Ollama: " + response.body());
            }

            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            return embedding;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Embedding request interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if Ollama is reachable.
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );

            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private record EmbedRequest(String model, String prompt) {}
}
