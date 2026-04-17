package com.github.pvtitov.aichat.dto;

/**
 * Represents a structured citation from a retrieved knowledge chunk.
 * Contains source metadata and the actual quote used from the chunk.
 */
public class CitationSource {
    private final int chunkId;
    private final String source;
    private final String title;
    private final String section;
    private final String quote;
    private final double relevanceScore;

    public CitationSource(int chunkId, String source, String title, String section,
                          String quote, double relevanceScore) {
        this.chunkId = chunkId;
        this.source = source;
        this.title = title;
        this.section = section;
        this.quote = quote;
        this.relevanceScore = relevanceScore;
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

    public String getQuote() {
        return quote;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    /**
     * Formats this citation as a readable string.
     */
    public String formatCitation() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s", title));
        if (section != null && !section.isEmpty()) {
            sb.append(" > ").append(section);
        }
        sb.append(String.format(", relevance: %.3f]", relevanceScore));
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format(
            "CitationSource{chunkId=%d, source='%s', title='%s', section='%s', relevance=%.3f}",
            chunkId, source, title, section, relevanceScore
        );
    }
}
