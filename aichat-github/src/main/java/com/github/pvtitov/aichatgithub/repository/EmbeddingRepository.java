package com.github.pvtitov.aichatgithub.repository;

import com.github.pvtitov.aichatgithub.constants.ApiConstants;
import com.github.pvtitov.aichatgithub.dto.EmbeddingSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EmbeddingRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingRepository.class);
    private Connection connection;
    
    public EmbeddingRepository() {
        initializeConnection();
    }
    
    private void initializeConnection() {
        String jdbcUrl = "jdbc:sqlite:" + ApiConstants.EMBEDDING_DB_PATH;
        try {
            connection = DriverManager.getConnection(jdbcUrl);
            System.out.println("[Connected to knowledge base: " + ApiConstants.EMBEDDING_DB_PATH + "]");
        } catch (SQLException e) {
            System.out.println("[Knowledge base not found: " + ApiConstants.EMBEDDING_DB_PATH + "]");
            connection = null;
        }
    }
    
    public boolean isReady() {
        return connection != null;
    }
    
    public List<EmbeddingEntry> findAllWithEmbeddings() {
        if (!isReady()) {
            return List.of();
        }
        
        String sql = "SELECT chunk_id, source, title, section, content, embedding FROM embedding_index ORDER BY chunk_id";
        List<EmbeddingEntry> entries = new ArrayList<>();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String chunkId = rs.getString("chunk_id");
                String source = rs.getString("source");
                String title = rs.getString("title");
                String section = rs.getString("section");
                String content = rs.getString("content");
                float[] embedding = byteArrayToFloatArray(rs.getBytes("embedding"));
                
                entries.add(new EmbeddingEntry(chunkId, source, title, section, content, embedding));
            }
        } catch (SQLException e) {
            logger.error("Failed to load embeddings", e);
        }
        
        return entries;
    }
    
    private float[] byteArrayToFloatArray(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new float[0];
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] result = new float[bytes.length / 4];
        for (int i = 0; i < result.length; i++) {
            result[i] = buffer.getFloat();
        }
        return result;
    }
    
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.error("Failed to close embedding database connection", e);
        }
    }
    
    public static class EmbeddingEntry {
        private final String chunkId;
        private final String source;
        private final String title;
        private final String section;
        private final String content;
        private final float[] embedding;
        
        public EmbeddingEntry(String chunkId, String source, String title, 
                             String section, String content, float[] embedding) {
            this.chunkId = chunkId;
            this.source = source;
            this.title = title;
            this.section = section;
            this.content = content;
            this.embedding = embedding;
        }
        
        public String getChunkId() { return chunkId; }
        public String getSource() { return source; }
        public String getTitle() { return title; }
        public String getSection() { return section; }
        public String getContent() { return content; }
        public float[] getEmbedding() { return embedding; }
    }
}
