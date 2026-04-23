package com.github.pvtitov.aichatclearning.repository;

import com.github.pvtitov.aichatclearning.constants.DatabaseConstants;
import com.github.pvtitov.aichatclearning.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DialogHistoryRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(DialogHistoryRepository.class);
    private Connection connection;
    
    public DialogHistoryRepository() {
        initializeDatabase();
    }
    
    private void initializeDatabase() {
        try {
            connection = DriverManager.getConnection(DatabaseConstants.DB_URL);
            connection.setAutoCommit(true);
            
            // Enable WAL mode for better performance
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute(DatabaseConstants.CREATE_DIALOG_HISTORY_TABLE);
                stmt.execute(DatabaseConstants.CREATE_TASKS_TABLE);
            }
            logger.info("Database initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    public ChatMessage save(ChatMessage message) {
        String sql = "INSERT INTO dialog_history (role, content, prompt_tokens, completion_tokens, total_tokens) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, message.getRole());
            pstmt.setString(2, message.getContent());
            pstmt.setObject(3, message.getPromptTokens());
            pstmt.setObject(4, message.getCompletionTokens());
            pstmt.setObject(5, message.getTotalTokens());
            
            pstmt.executeUpdate();
            
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    message.setId(rs.getLong(1));
                }
            }
            return message;
        } catch (SQLException e) {
            logger.error("Failed to save chat message", e);
            throw new RuntimeException("Failed to save chat message", e);
        }
    }
    
    public List<ChatMessage> findAll() {
        String sql = "SELECT * FROM dialog_history ORDER BY timestamp ASC";
        List<ChatMessage> messages = new ArrayList<>();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                messages.add(mapResultSetToChatMessage(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to find all chat messages", e);
            throw new RuntimeException("Failed to retrieve chat messages", e);
        }
        
        return messages;
    }
    
    public void deleteAll() {
        String sql = "DELETE FROM dialog_history";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            logger.info("All dialog history cleared");
        } catch (SQLException e) {
            logger.error("Failed to delete all messages", e);
            throw new RuntimeException("Failed to clear dialog history", e);
        }
    }
    
    private ChatMessage mapResultSetToChatMessage(ResultSet rs) throws SQLException {
        ChatMessage message = new ChatMessage();
        message.setId(rs.getLong("id"));
        message.setRole(rs.getString("role"));
        message.setContent(rs.getString("content"));
        message.setPromptTokens(rs.getInt("prompt_tokens"));
        message.setCompletionTokens(rs.getInt("completion_tokens"));
        message.setTotalTokens(rs.getInt("total_tokens"));
        return message;
    }
    
    public Connection getConnection() {
        return connection;
    }
    
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.error("Failed to close database connection", e);
        }
    }
}
