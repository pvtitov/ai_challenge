package com.github.pvtitov.aichat.repository;

import com.github.pvtitov.aichat.constants.DatabaseConstants;
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
    public void saveShortTerm(ChatMessage message) {
        save(message, DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE);
    }

    @Override
    public void saveMidTerm(ChatMessage message) {
        save(message, DatabaseConstants.CHAT_HISTORY_MID_TERM_TABLE);
    }

    @Override
    public void saveLongTerm(ChatMessage message) {
        save(message, DatabaseConstants.CHAT_HISTORY_LONG_TERM_TABLE);
    }

    @Override
    public List<ChatMessage> findShortTermByBranch(int branch) {
        return findByBranch(branch, DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE);
    }

    @Override
    public List<ChatMessage> findMidTermByBranch(int branch) {
        return findByBranch(branch, DatabaseConstants.CHAT_HISTORY_MID_TERM_TABLE);
    }

    @Override
    public List<ChatMessage> findLongTermByBranch(int branch) {
        return findByBranch(branch, DatabaseConstants.CHAT_HISTORY_LONG_TERM_TABLE);
    }

    @Override
    public void deleteByBranch(int branch) {
        deleteByBranch(branch, DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE);
        deleteByBranch(branch, DatabaseConstants.CHAT_HISTORY_MID_TERM_TABLE);
        deleteByBranch(branch, DatabaseConstants.CHAT_HISTORY_LONG_TERM_TABLE);
    }

    @Override
    public void deleteAll() {
        deleteAll(DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE);
        deleteAll(DatabaseConstants.CHAT_HISTORY_MID_TERM_TABLE);
        deleteAll(DatabaseConstants.CHAT_HISTORY_LONG_TERM_TABLE);
    }

    @Override
    public int getMaxBranch() {
        String sql = "SELECT MAX(branch) FROM " + DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE;
        Integer maxBranch = jdbcTemplate.queryForObject(sql, Integer.class);
        return maxBranch == null ? 0 : maxBranch;
    }

    @Override
    public List<Integer> getBranches() {
        String sql = "SELECT DISTINCT branch FROM " + DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE + " ORDER BY branch";
        return jdbcTemplate.queryForList(sql, Integer.class);
    }

    @Override
    public long getCumulativeTokens(int branch) {
        String sql = "SELECT SUM(total_tokens) FROM " + DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE + " WHERE branch = ?";
        Long cumulativeTokens = jdbcTemplate.queryForObject(sql, Long.class, branch);
        return cumulativeTokens == null ? 0 : cumulativeTokens;
    }

    private void save(ChatMessage message, String tableName) {
        String sql = "INSERT INTO " + tableName + " (branch, role, content, prompt_tokens, completion_tokens, total_tokens) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, message.getBranch(), message.getRole(), message.getContent(),
                message.getPromptTokens(), message.getCompletionTokens(), message.getTotalTokens());
    }

    private List<ChatMessage> findByBranch(int branch, String tableName) {
        String sql = "SELECT * FROM " + tableName + " WHERE branch = ? ORDER BY timestamp";
        return jdbcTemplate.query(sql, this::mapRowToChatMessage, branch);
    }

    private void deleteByBranch(int branch, String tableName) {
        String sql = "DELETE FROM " + tableName + " WHERE branch = ?";
        jdbcTemplate.update(sql, branch);
    }

    private void deleteAll(String tableName) {
        String sql = "DELETE FROM " + tableName;
        jdbcTemplate.update(sql);
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
