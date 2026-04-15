package com.github.pvtitov.aichat.dto;

/**
 * Represents a result from embedding similarity search.
 */
public class EmbeddingSearchResult {
    private final int chunkId;
    private final String source;
    private final String title;
    private final String section;
    private final String content;
    private final double similarityScore;

    public EmbeddingSearchResult(int chunkId, String source, String title, String section, 
                                  String content, double similarityScore) {
        this.chunkId = chunkId;
        this.source = source;
        this.title = title;
        this.section = section;
        this.content = content;
        this.similarityScore = similarityScore;
    }

    public int getChunkId() {
        return chunkId;
    }

    public String getSource() {
        return source;
    }

    public String getTitle() {
        return title;
    }

    public String getSection() {
        return section;
    }

    public String getContent() {
        return content;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    @Override
    public String toString() {
        return String.format(
            "[%.3f] %s | %s | Section: %s%n    %s",
            similarityScore, title, source, section,
            content.length() > 150 ? content.substring(0, 150) + "..." : content
        );
    }
}
