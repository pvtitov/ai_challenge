package com.github.pvtitov.aichat.repository;

import com.github.pvtitov.aichat.model.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class ChatHistoryRepositoryImpl implements ChatHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatHistoryRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(ChatMessage message) {
        String sql = "INSERT INTO chat_history (branch, role, content, prompt_tokens, completion_tokens, total_tokens) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, message.getBranch(), message.getRole(), message.getContent(),
                message.getPromptTokens(), message.getCompletionTokens(), message.getTotalTokens());
    }

    @Override
    public List<ChatMessage> findByBranch(int branch) {
        String sql = "SELECT * FROM chat_history WHERE branch = ? ORDER BY timestamp";
        return jdbcTemplate.query(sql, this::mapRowToChatMessage, branch);
    }

    @Override
    public void deleteByBranch(int branch) {
        String sql = "DELETE FROM chat_history WHERE branch = ?";
        jdbcTemplate.update(sql, branch);
    }

    @Override
    public int getMaxBranch() {
        String sql = "SELECT MAX(branch) FROM chat_history";
        Integer maxBranch = jdbcTemplate.queryForObject(sql, Integer.class);
        return maxBranch == null ? 0 : maxBranch;
    }

    @Override
    public List<Integer> getBranches() {
        String sql = "SELECT DISTINCT branch FROM chat_history ORDER BY branch";
        return jdbcTemplate.queryForList(sql, Integer.class);
    }

    @Override
    public long getCumulativeTokens(int branch) {
        String sql = "SELECT SUM(total_tokens) FROM chat_history WHERE branch = ?";
        Long cumulativeTokens = jdbcTemplate.queryForObject(sql, Long.class, branch);
        return cumulativeTokens == null ? 0 : cumulativeTokens;
    }

    private ChatMessage mapRowToChatMessage(ResultSet rs, int rowNum) throws SQLException {
        ChatMessage message = new ChatMessage();
        message.setId(rs.getLong("id"));
        message.setBranch(rs.getInt("branch"));
        message.setRole(rs.getString("role"));
        message.setContent(rs.getString("content"));
        int promptTokens = rs.getInt("prompt_tokens");
        if (!rs.wasNull()) {
            message.setPromptTokens(promptTokens);
        }
        int completionTokens = rs.getInt("completion_tokens");
        if (!rs.wasNull()) {
            message.setCompletionTokens(completionTokens);
        }
        int totalTokens = rs.getInt("total_tokens");
        if (!rs.wasNull()) {
            message.setTotalTokens(totalTokens);
        }
        message.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
        return message;
    }
}
