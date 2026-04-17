package com.github.pvtitov.aichat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pvtitov.aichat.dto.CitationSource;
import com.github.pvtitov.aichat.dto.EmbeddingSearchResult;
import com.github.pvtitov.aichat.repository.EmbeddingRepository;
import com.github.pvtitov.aichat.repository.EmbeddingRepository.EmbeddingEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for performing semantic search over saved embeddings.
 * Uses Ollama to generate query embeddings and cosine similarity to find matches.
 */
@Service
public class EmbeddingSearchService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSearchService.class);

    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "nomic-embed-text";
    private static final int DEFAULT_TOP_K_BEFORE_RERANK = 10;
    private static final int DEFAULT_TOP_K_AFTER_RERANK = 5;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.6;

    private final String ollamaUrl;
    private final String model;
    private final int topKBeforeRerank;
    private final int topKAfterRerank;
    private final double similarityThreshold;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private EmbeddingRepository embeddingRepository;

    public EmbeddingSearchService() {
        this(DEFAULT_OLLAMA_URL, DEFAULT_MODEL, DEFAULT_TOP_K_BEFORE_RERANK, DEFAULT_TOP_K_AFTER_RERANK, DEFAULT_SIMILARITY_THRESHOLD);
    }

    public EmbeddingSearchService(String ollamaUrl, String model, int topKBeforeRerank, int topKAfterRerank, double similarityThreshold) {
        this.ollamaUrl = ollamaUrl;
        this.model = model;
        this.topKBeforeRerank = topKBeforeRerank;
        this.topKAfterRerank = topKAfterRerank;
        this.similarityThreshold = similarityThreshold;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Sets the embedding repository to search against.
     */
    public void setEmbeddingRepository(EmbeddingRepository repository) {
        this.embeddingRepository = repository;
    }

    /**
     * Checks if the embedding search service is ready (Ollama available and repository initialized).
     */
    public boolean isReady() {
        return embeddingRepository != null && embeddingRepository.isConnected() 
                && embeddingRepository.hasEmbeddingTable() && isOllamaAvailable();
    }

    /**
     * Searches for relevant chunks based on the query text.
     * 
     * @param query the search query
     * @return list of search results ordered by similarity score (descending)
     */
    public List<EmbeddingSearchResult> search(String query) {
        if (embeddingRepository == null || !embeddingRepository.isConnected()) {
            log.warn("Embedding repository not available for search");
            return List.of();
        }

        if (!embeddingRepository.hasEmbeddingTable()) {
            log.warn("Embedding index table does not exist");
            return List.of();
        }

        if (!isOllamaAvailable()) {
            log.warn("Ollama not available for embedding generation");
            return List.of();
        }

        try {
            // 2. Generate embedding for the rewritten query
            float[] queryEmbedding = generateEmbedding(query);
            if (queryEmbedding == null) {
                log.error("Failed to generate query embedding");
                return List.of();
            }

            // 3. Retrieve all embeddings and compute similarity
            List<EmbeddingEntry> allEntries = embeddingRepository.findAllWithEmbeddings();
            
            List<EmbeddingSearchResult> initialResults = new ArrayList<>();
            for (EmbeddingEntry entry : allEntries) {
                double similarity = cosineSimilarity(queryEmbedding, entry.getEmbedding());
                initialResults.add(new EmbeddingSearchResult(
                        entry.getChunkId(),
                        entry.getSource(),
                        entry.getTitle(),
                        entry.getSection(),
                        entry.getContent(),
                        similarity
                ));
            }

            // Sort by similarity and take topKBeforeRerank
            List<EmbeddingSearchResult> topKBeforeRerankResults = initialResults.stream()
                    .sorted(Comparator.comparingDouble(EmbeddingSearchResult::getSimilarityScore).reversed())
                    .limit(topKBeforeRerank)
                    .collect(Collectors.toList());

            // 4. Apply Reranking/Filtering (using similarity threshold and topKAfterRerank)
            return topKBeforeRerankResults.stream()
                    .filter(result -> result.getSimilarityScore() >= similarityThreshold)
                    .sorted(Comparator.comparingDouble(EmbeddingSearchResult::getSimilarityScore).reversed())
                    .limit(topKAfterRerank)
                    .collect(Collectors.toList());

        } catch (SQLException e) {
            log.error("Database error during embedding search", e);
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected error during embedding search", e);
            return List.of();
        }
    }

    /**
     * Generates a formatted context string from search results for inclusion in LLM prompts.
     *
     * @param results the search results
     * @return formatted string with relevant knowledge chunks
     */
    public String formatResultsAsContext(List<EmbeddingSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== Relevant Knowledge Retrieved ===\n");

        for (int i = 0; i < results.size(); i++) {
            EmbeddingSearchResult result = results.get(i);
            sb.append(String.format("\n[Result %d] (similarity: %.3f)\n", i + 1, result.getSimilarityScore()));
            sb.append(String.format("Source: %s\n", result.getTitle()));
            if (result.getSection() != null && !result.getSection().isEmpty()) {
                sb.append(String.format("Section: %s\n", result.getSection()));
            }
            sb.append(String.format("Content:\n%s\n", result.getContent()));
        }

        sb.append("\n=== End of Retrieved Knowledge ===\n");
        return sb.toString();
    }

    /**
     * Wrapper class holding both formatted context and structured citation sources.
     */
    public static class SearchContext {
        private final String formattedContext;
        private final List<CitationSource> citations;
        private final double maxRelevanceScore;

        public SearchContext(String formattedContext, List<CitationSource> citations, double maxRelevanceScore) {
            this.formattedContext = formattedContext;
            this.citations = citations;
            this.maxRelevanceScore = maxRelevanceScore;
        }

        public String getFormattedContext() {
            return formattedContext;
        }

        public List<CitationSource> getCitations() {
            return citations;
        }

        public double getMaxRelevanceScore() {
            return maxRelevanceScore;
        }

        public boolean hasResults() {
            return !citations.isEmpty();
        }
    }

    /**
     * Generates both formatted context and structured citation sources from search results.
     *
     * @param results the search results
     * @return SearchContext containing formatted text and structured citations
     */
    public SearchContext formatResultsAsContextWithCitations(List<EmbeddingSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return new SearchContext("", List.of(), 0.0);
        }

        StringBuilder contextSb = new StringBuilder();
        contextSb.append("\n\n=== Relevant Knowledge Retrieved ===\n");

        List<CitationSource> citations = new ArrayList<>();
        double maxScore = 0.0;

        for (int i = 0; i < results.size(); i++) {
            EmbeddingSearchResult result = results.get(i);
            contextSb.append(String.format("\n[Result %d] (similarity: %.3f)\n", i + 1, result.getSimilarityScore()));
            contextSb.append(String.format("Source: %s\n", result.getTitle()));
            if (result.getSection() != null && !result.getSection().isEmpty()) {
                contextSb.append(String.format("Section: %s\n", result.getSection()));
            }
            contextSb.append(String.format("Content:\n%s\n", result.getContent()));

            // Extract a quote (first 200 chars or full content if shorter)
            String quote = result.getContent().length() > 200 
                ? result.getContent().substring(0, 200) + "..." 
                : result.getContent();
            
            CitationSource citation = new CitationSource(
                result.getChunkId(),
                result.getSource(),
                result.getTitle(),
                result.getSection(),
                quote,
                result.getSimilarityScore()
            );
            citations.add(citation);
            
            maxScore = Math.max(maxScore, result.getSimilarityScore());
        }

        contextSb.append("\n=== End of Retrieved Knowledge ===\n");
        
        return new SearchContext(contextSb.toString(), citations, maxScore);
    }

    /**
     * Generates an embedding for the given text using Ollama.
     */
    private float[] generateEmbedding(String text) {
        try {
            String requestBody = objectMapper.writeValueAsString(
                    new EmbedRequest(model, text)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/embeddings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                log.error("Ollama API error (status {}): {}", response.statusCode(), response.body());
                return null;
            }

            JsonNode jsonNode = objectMapper.readTree(response.body());
            JsonNode embeddingNode = jsonNode.get("embedding");

            if (embeddingNode == null || !embeddingNode.isArray()) {
                log.error("Invalid embedding response from Ollama: {}", response.body());
                return null;
            }

            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            return embedding;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Embedding request interrupted", e);
            return null;
        } catch (IOException e) {
            log.error("Failed to generate embedding", e);
            return null;
        }
    }

    /**
     * Computes cosine similarity between two vectors.
     */
    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        double denominator = Math.sqrt(norm1) * Math.sqrt(norm2);
        if (denominator == 0.0) {
            return 0.0;
        }

        return dotProduct / denominator;
    }

    /**
     * Checks if Ollama is reachable.
     */
    private boolean isOllamaAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/tags"))
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
