package com.github.pvtitov.aichat.repository;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for accessing embedding vectors stored in the embeddings database.
 * This connects to the same embeddings.db used by the embedding-tool project.
 */
public class EmbeddingRepository implements AutoCloseable {

    private final String dbPath;
    private Connection connection;

    public EmbeddingRepository(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * Initializes the repository and opens a connection to the database.
     */
    public void initialize() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        connection.setAutoCommit(true);
    }

    /**
     * Checks if the database is accessible.
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Checks if the embedding_index table exists in the database.
     */
    public boolean hasEmbeddingTable() {
        try {
            String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name='embedding_index'";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Retrieves all chunks with their embedding vectors.
     * Note: This can be memory-intensive for large databases.
     */
    public List<EmbeddingEntry> findAllWithEmbeddings() throws SQLException {
        List<EmbeddingEntry> entries = new ArrayList<>();
        String sql = "SELECT chunk_id, source, title, section, content, embedding FROM embedding_index ORDER BY chunk_id";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                entries.add(new EmbeddingEntry(
                        rs.getInt("chunk_id"),
                        rs.getString("source"),
                        rs.getString("title"),
                        rs.getString("section"),
                        rs.getString("content"),
                        bytesToFloatArray(rs.getBytes("embedding"))
                ));
            }
        }
        return entries;
    }

    /**
     * Returns the total count of entries in the embedding index.
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM embedding_index";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /**
     * Converts bytes back to float array (reverse of floatArrayToBytes).
     */
    private float[] bytesToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    /**
     * Represents an entry with its embedding vector.
     */
    public static class EmbeddingEntry {
        private final int chunkId;
        private final String source;
        private final String title;
        private final String section;
        private final String content;
        private final float[] embedding;

        public EmbeddingEntry(int chunkId, String source, String title, String section, 
                              String content, float[] embedding) {
            this.chunkId = chunkId;
            this.source = source;
            this.title = title;
            this.section = section;
            this.content = content;
            this.embedding = embedding;
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

        public float[] getEmbedding() {
            return embedding;
        }
    }
}
