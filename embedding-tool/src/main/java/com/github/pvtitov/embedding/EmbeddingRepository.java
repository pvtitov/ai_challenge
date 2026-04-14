package com.github.pvtitov.embedding;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite repository for storing and retrieving embedding index.
 */
public class EmbeddingRepository implements AutoCloseable {

    private static final String DEFAULT_DB_PATH = "embeddings.db";

    private final String dbPath;
    private Connection connection;

    public EmbeddingRepository() {
        this(DEFAULT_DB_PATH);
    }

    public EmbeddingRepository(String dbPath) {
        this.dbPath = dbPath;
    }

    public void initialize() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        connection.setAutoCommit(true);

        // Enable WAL mode for better performance
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
        }

        createTable();
    }

    private void createTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS embedding_index (
                    chunk_id INTEGER PRIMARY KEY,
                    source TEXT NOT NULL,
                    title TEXT NOT NULL,
                    section TEXT,
                    content TEXT NOT NULL,
                    embedding BLOB NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Saves a chunk with its embedding to the database.
     */
    public void save(Chunk chunk) throws SQLException {
        String sql = """
                INSERT OR REPLACE INTO embedding_index 
                (chunk_id, source, title, section, content, embedding)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, chunk.getChunkId());
            pstmt.setString(2, chunk.getSource());
            pstmt.setString(3, chunk.getTitle());
            pstmt.setString(4, chunk.getSection() != null ? chunk.getSection() : "");
            pstmt.setString(5, chunk.getContent());
            pstmt.setBytes(6, floatArrayToBytes(chunk.getEmbedding()));
            pstmt.executeUpdate();
        }
    }

    /**
     * Saves multiple chunks in a batch.
     */
    public void saveBatch(List<Chunk> chunks) throws SQLException {
        connection.setAutoCommit(false);
        try {
            String sql = """
                    INSERT OR REPLACE INTO embedding_index 
                    (chunk_id, source, title, section, content, embedding)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                for (Chunk chunk : chunks) {
                    pstmt.setInt(1, chunk.getChunkId());
                    pstmt.setString(2, chunk.getSource());
                    pstmt.setString(3, chunk.getTitle());
                    pstmt.setString(4, chunk.getSection() != null ? chunk.getSection() : "");
                    pstmt.setString(5, chunk.getContent());
                    pstmt.setBytes(6, floatArrayToBytes(chunk.getEmbedding()));
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            connection.commit();
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Retrieves all entries from the index.
     */
    public List<IndexedEntry> findAll() throws SQLException {
        List<IndexedEntry> entries = new ArrayList<>();
        String sql = "SELECT chunk_id, source, title, section, content, created_at FROM embedding_index ORDER BY chunk_id";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                entries.add(new IndexedEntry(
                        rs.getInt("chunk_id"),
                        rs.getString("source"),
                        rs.getString("title"),
                        rs.getString("section"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at")
                ));
            }
        }
        return entries;
    }

    /**
     * Returns total count of entries in the index.
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

    /**
     * Gets the next available chunk ID.
     */
    public int getNextChunkId() throws SQLException {
        String sql = "SELECT MAX(chunk_id) as max_id FROM embedding_index";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                int maxId = rs.getInt("max_id");
                return rs.wasNull() ? 1 : maxId + 1;
            }
            return 1;
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /**
     * Converts float array to bytes for storage.
     */
    private byte[] floatArrayToBytes(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    /**
     * Represents an indexed entry (without embedding vector for display).
     */
    public record IndexedEntry(
            int chunkId,
            String source,
            String title,
            String section,
            String content,
            Timestamp createdAt
    ) {
        @Override
        public String toString() {
            return String.format(
                    "[%d] %s | %s | Section: %s%n    %s",
                    chunkId, title, source, section,
                    content.length() > 120 ? content.substring(0, 120) + "..." : content
            );
        }
    }
}
