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
    public List<ChatMessage> findShortTermByBranch(int branch, String profileLogin) {
        return findByBranch(branch, profileLogin, DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE);
    }

    @Override
    public List<ChatMessage> findMidTermByBranch(int branch, String profileLogin) {
        return findByBranch(branch, profileLogin, DatabaseConstants.CHAT_HISTORY_MID_TERM_TABLE);
    }

    @Override
    public List<ChatMessage> findLongTermByBranch(int branch, String profileLogin) {
        return findByBranch(branch, profileLogin, DatabaseConstants.CHAT_HISTORY_LONG_TERM_TABLE);
    }

    @Override
    public void deleteByBranch(int branch, String profileLogin) {
        deleteByBranch(branch, profileLogin, DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE);
        deleteByBranch(branch, profileLogin, DatabaseConstants.CHAT_HISTORY_MID_TERM_TABLE);
        deleteByBranch(branch, profileLogin, DatabaseConstants.CHAT_HISTORY_LONG_TERM_TABLE);
    }

    @Override
    public void deleteShortTermByBranch(int branch, String profileLogin) {
        deleteByBranch(branch, profileLogin, DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE);
    }

    @Override
    public void deleteMidTermByBranch(int branch, String profileLogin) {
        deleteByBranch(branch, profileLogin, DatabaseConstants.CHAT_HISTORY_MID_TERM_TABLE);
    }

    @Override
    public void deleteLongTermByBranch(int branch, String profileLogin) {
        deleteByBranch(branch, profileLogin, DatabaseConstants.CHAT_HISTORY_LONG_TERM_TABLE);
    }

    @Override
    public void deleteAll(String profileLogin) {
        deleteAll(profileLogin, DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE);
        deleteAll(profileLogin, DatabaseConstants.CHAT_HISTORY_MID_TERM_TABLE);
        deleteAll(profileLogin, DatabaseConstants.CHAT_HISTORY_LONG_TERM_TABLE);
    }

    @Override
    public int getMaxBranch(String profileLogin) {
        String sql = "SELECT MAX(branch) FROM " + DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE + " WHERE profile_login = ?";
        Integer maxBranch = jdbcTemplate.queryForObject(sql, Integer.class, profileLogin);
        return maxBranch == null ? 0 : maxBranch;
    }

    @Override
    public List<Integer> getBranches(String profileLogin) {
        String sql = "SELECT DISTINCT branch FROM " + DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE + " WHERE profile_login = ? ORDER BY branch";
        return jdbcTemplate.queryForList(sql, Integer.class, profileLogin);
    }

    @Override
    public long getCumulativeTokens(int branch, String profileLogin) {
        String sql = "SELECT SUM(total_tokens) FROM (" +
                "SELECT DISTINCT id, total_tokens FROM " + DatabaseConstants.CHAT_HISTORY_SHORT_TERM_TABLE + " WHERE branch = ? AND profile_login = ? " +
                "UNION " +
                "SELECT DISTINCT id, total_tokens FROM " + DatabaseConstants.CHAT_HISTORY_MID_TERM_TABLE + " WHERE branch = ? AND profile_login = ? " +
                "UNION " +
                "SELECT DISTINCT id, total_tokens FROM " + DatabaseConstants.CHAT_HISTORY_LONG_TERM_TABLE + " WHERE branch = ? AND profile_login = ?" +
                ") AS combined_history";

        Long cumulativeTokens = jdbcTemplate.queryForObject(sql, Long.class,
                branch, profileLogin,
                branch, profileLogin,
                branch, profileLogin);

        return cumulativeTokens == null ? 0 : cumulativeTokens;
    }

    private void save(ChatMessage message, String tableName) {
        String sql = "INSERT INTO " + tableName + " (branch, role, content, prompt_tokens, completion_tokens, total_tokens, profile_login) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, message.getBranch(), message.getRole(), message.getContent(),
                message.getPromptTokens(), message.getCompletionTokens(), message.getTotalTokens(), message.getProfileLogin());
    }

    private List<ChatMessage> findByBranch(int branch, String profileLogin, String tableName) {
        String sql = "SELECT * FROM " + tableName + " WHERE branch = ? AND profile_login = ? ORDER BY timestamp";
        return jdbcTemplate.query(sql, this::mapRowToChatMessage, branch, profileLogin);
    }

    private void deleteByBranch(int branch, String profileLogin, String tableName) {
        String sql = "DELETE FROM " + tableName + " WHERE branch = ? AND profile_login = ?";
        jdbcTemplate.update(sql, branch, profileLogin);
    }

    private void deleteAll(String profileLogin, String tableName) {
        String sql = "DELETE FROM " + tableName + " WHERE profile_login = ?";
        jdbcTemplate.update(sql, profileLogin);
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
        message.setProfileLogin(rs.getString("profile_login"));
        message.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
        return message;
    }
}
