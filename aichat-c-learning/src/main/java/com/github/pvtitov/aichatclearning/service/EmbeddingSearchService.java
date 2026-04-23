package com.github.pvtitov.aichatclearning.service;

import com.github.pvtitov.aichatclearning.constants.ApiConstants;
import com.github.pvtitov.aichatclearning.dto.EmbeddingSearchResult;
import com.github.pvtitov.aichatclearning.repository.EmbeddingRepository;
import com.github.pvtitov.aichatclearning.repository.EmbeddingRepository.EmbeddingEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class EmbeddingSearchService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingSearchService.class);
    
    private final EmbeddingRepository embeddingRepository;
    private final HttpClient httpClient;
    private final String ollamaUrl;
    private final String ollamaModel;
    private final int topK;
    private final double similarityThreshold;
    
    public EmbeddingSearchService() {
        this(ApiConstants.OLLAMA_URL, ApiConstants.OLLAMA_MODEL, 5, 0.7);
    }
    
    public EmbeddingSearchService(String ollamaUrl, String ollamaModel, int topK, double similarityThreshold) {
        this.embeddingRepository = new EmbeddingRepository();
        this.httpClient = HttpClient.newHttpClient();
        this.ollamaUrl = ollamaUrl;
        this.ollamaModel = ollamaModel;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }
    
    public boolean isReady() {
        return embeddingRepository.isReady();
    }
    
    public List<EmbeddingSearchResult> search(String query) {
        if (!isReady()) {
            return List.of();
        }

        try {
            // Generate embedding for query
            float[] queryEmbedding = generateEmbedding(query);
            if (queryEmbedding == null || queryEmbedding.length == 0) {
                System.out.println("[Failed to generate embedding via Ollama]");
                return List.of();
            }

            // Load all embeddings and compute cosine similarity
            List<EmbeddingEntry> allEntries = embeddingRepository.findAllWithEmbeddings();
            System.out.println("[Searching through " + allEntries.size() + " indexed chunks]");
            List<EmbeddingSearchResult> results = allEntries.stream()
                .map(entry -> {
                    double similarity = cosineSimilarity(queryEmbedding, entry.getEmbedding());
                    return new EmbeddingSearchResult(
                        entry.getChunkId(),
                        entry.getSource(),
                        entry.getTitle(),
                        entry.getSection(),
                        entry.getContent(),
                        similarity
                    );
                })
                .sorted(Comparator.comparingDouble(EmbeddingSearchResult::getSimilarityScore).reversed())
                .filter(result -> result.getSimilarityScore() >= similarityThreshold)
                .limit(topK)
                .collect(Collectors.toList());

            return results;
        } catch (Exception e) {
            System.out.println("[Error searching embeddings: " + e.getMessage() + "]");
            return List.of();
        }
    }
    
    private float[] generateEmbedding(String text) throws IOException, InterruptedException {
        String requestBody = String.format(
            "{\"model\": \"%s\", \"prompt\": \"%s\"}",
            ollamaModel,
            text.replace("\"", "\\\"")
        );
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/embeddings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            logger.error("Ollama API returned error: {}", response.statusCode());
            return null;
        }
        
        // Parse response: {"embedding": [0.1, 0.2, ...]}
        String responseBody = response.body();
        int startIndex = responseBody.indexOf("\"embedding\":[");
        if (startIndex == -1) {
            return null;
        }
        
        startIndex += "\"embedding\":[".length();
        int endIndex = responseBody.indexOf("]", startIndex);
        if (endIndex == -1) {
            return null;
        }
        
        String embeddingStr = responseBody.substring(startIndex, endIndex);
        String[] parts = embeddingStr.split(",");
        float[] embedding = new float[parts.length];
        
        for (int i = 0; i < parts.length; i++) {
            embedding[i] = Float.parseFloat(parts[i].trim());
        }
        
        return embedding;
    }
    
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    public String formatResultsAsContext(List<EmbeddingSearchResult> results) {
        if (results.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("The following information was retrieved from the knowledge base:\n\n");
        
        for (int i = 0; i < results.size(); i++) {
            EmbeddingSearchResult result = results.get(i);
            sb.append("[").append(i + 1).append("] ");
            if (result.getTitle() != null && !result.getTitle().isEmpty()) {
                sb.append(result.getTitle()).append(" ");
            }
            if (result.getSection() != null && !result.getSection().isEmpty()) {
                sb.append("(").append(result.getSection()).append(") ");
            }
            sb.append("\n").append(result.getContent()).append("\n\n");
        }
        
        sb.append("Use this information to answer the user's question.\n");
        return sb.toString();
    }
    
    public void close() {
        embeddingRepository.close();
    }
}
