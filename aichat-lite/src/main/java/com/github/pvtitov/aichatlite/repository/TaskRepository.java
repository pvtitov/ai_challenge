package com.github.pvtitov.aichatlite.repository;

import com.github.pvtitov.aichatlite.dto.TaskCompletionStatus;
import com.github.pvtitov.aichatlite.model.Task;
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
        String sql = "INSERT INTO tasks (title, requirements, is_completed, reason) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, task.getTitle());
            pstmt.setString(2, serializeRequirements(task.getRequirements()));
            pstmt.setBoolean(3, task.getStatus() != null && task.getStatus().isCompleted());
            pstmt.setString(4, task.getStatus() != null ? task.getStatus().getReason() : null);

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

    public Task findById(Long id) {
        String sql = "SELECT * FROM tasks WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToTask(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find task by id", e);
            throw new RuntimeException("Failed to retrieve task", e);
        }
        return null;
    }

    public Task findLatest() {
        String sql = "SELECT * FROM tasks ORDER BY id DESC LIMIT 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return mapResultSetToTask(rs);
            }
        } catch (SQLException e) {
            logger.error("Failed to find latest task", e);
            throw new RuntimeException("Failed to retrieve latest task", e);
        }
        return null;
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
        task.setTitle(rs.getString("title"));
        task.setRequirements(deserializeRequirements(rs.getString("requirements")));

        TaskCompletionStatus status = new TaskCompletionStatus();
        status.setCompleted(rs.getBoolean("is_completed"));
        status.setReason(rs.getString("reason"));
        task.setStatus(status);

        return task;
    }

    private String serializeRequirements(List<String> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return null;
        }
        return String.join("\n---REQ---\n", requirements);
    }

    private List<String> deserializeRequirements(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        for (String req : text.split("\n---REQ---\n")) {
            String trimmed = req.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
