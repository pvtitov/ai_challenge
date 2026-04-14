package com.github.pvtitov.embedding;

/**
 * Represents a chunk of text with metadata and optional embedding.
 */
public class Chunk {
    private int chunkId;
    private String source;
    private String title;
    private String section;
    private String content;
    private float[] embedding;

    public Chunk() {}

    public Chunk(int chunkId, String source, String title, String section, String content) {
        this.chunkId = chunkId;
        this.source = source;
        this.title = title;
        this.section = section;
        this.content = content;
    }

    public int getChunkId() {
        return chunkId;
    }

    public void setChunkId(int chunkId) {
        this.chunkId = chunkId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "chunkId=" + chunkId +
                ", source='" + source + '\'' +
                ", title='" + title + '\'' +
                ", section='" + section + '\'' +
                ", content='" + (content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content) + '\'' +
                '}';
    }
}
