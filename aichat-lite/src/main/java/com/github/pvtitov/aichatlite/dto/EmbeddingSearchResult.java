package com.github.pvtitov.aichatlite.dto;

public class EmbeddingSearchResult {
    
    private String chunkId;
    private String source;
    private String title;
    private String section;
    private String content;
    private double similarityScore;
    
    public EmbeddingSearchResult(String chunkId, String source, String title, 
                                  String section, String content, double similarityScore) {
        this.chunkId = chunkId;
        this.source = source;
        this.title = title;
        this.section = section;
        this.content = content;
        this.similarityScore = similarityScore;
    }
    
    public String getChunkId() {
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
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            sb.append(title);
        }
        if (section != null && !section.isEmpty()) {
            sb.append(" (").append(section).append(")");
        }
        if (source != null && !source.isEmpty()) {
            sb.append(" - Source: ").append(source);
        }
        return sb.toString();
    }
}
