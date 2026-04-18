package com.github.pvtitov.aichatlite.repository;

import com.github.pvtitov.aichatlite.constants.DatabaseConstants;
import com.github.pvtitov.aichatlite.model.Task;
import com.github.pvtitov.aichatlite.model.Task.Verification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TaskRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskRepository.class);
    private Connection connection;
    
    public TaskRepository(Connection connection) {
        this.connection = connection;
    }
    
    public Task save(Task task) {
        String sql = "INSERT INTO tasks (dialog_message_id, title, requirements, invariants, verified, verification_summary) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setObject(1, task.getDialogMessageId());
            pstmt.setString(2, task.getTitle());
            pstmt.setString(3, listToJson(task.getRequirements()));
            pstmt.setString(4, listToJson(task.getInvariants()));
            pstmt.setBoolean(5, task.getVerification() != null && task.getVerification().isVerified());
            pstmt.setString(6, task.getVerification() != null ? task.getVerification().getSummary() : null);
            
            pstmt.executeUpdate();
            
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    task.setId(rs.getLong(1));
                }
            }
            return task;
        } catch (SQLException e) {
            logger.error("Failed to save task", e);
            throw new RuntimeException("Failed to save task", e);
        }
    }
    
    public List<Task> findAll() {
        String sql = "SELECT * FROM tasks ORDER BY id ASC";
        List<Task> tasks = new ArrayList<>();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                tasks.add(mapResultSetToTask(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to find all tasks", e);
            throw new RuntimeException("Failed to retrieve tasks", e);
        }
        
        return tasks;
    }
    
    public void deleteAll() {
        String sql = "DELETE FROM tasks";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            logger.info("All tasks cleared");
        } catch (SQLException e) {
            logger.error("Failed to delete all tasks", e);
            throw new RuntimeException("Failed to clear tasks", e);
        }
    }
    
    private Task mapResultSetToTask(ResultSet rs) throws SQLException {
        Task task = new Task();
        task.setId(rs.getLong("id"));
        task.setDialogMessageId(rs.getLong("dialog_message_id"));
        task.setTitle(rs.getString("title"));
        task.setRequirements(jsonToList(rs.getString("requirements")));
        task.setInvariants(jsonToList(rs.getString("invariants")));
        
        Verification verification = new Verification();
        verification.setVerified(rs.getBoolean("verified"));
        verification.setSummary(rs.getString("verification_summary"));
        task.setVerification(verification);
        
        return task;
    }
    
    private String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(list.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
    
    private List<String> jsonToList(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return result;
        }
        // Simple JSON array parsing
        json = json.trim();
        if (json.startsWith("[")) {
            json = json.substring(1);
        }
        if (json.endsWith("]")) {
            json = json.substring(0, json.length() - 1);
        }
        
        String[] items = json.split(",");
        for (String item : items) {
            item = item.trim();
            if (item.startsWith("\"") && item.endsWith("\"")) {
                item = item.substring(1, item.length() - 1);
                item = unescapeJson(item);
                result.add(item);
            }
        }
        return result;
    }
    
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    private String unescapeJson(String value) {
        return value.replace("\\n", "\n")
                   .replace("\\r", "\r")
                   .replace("\\t", "\t")
                   .replace("\\\"", "\"")
                   .replace("\\\\", "\\");
    }
}
