package com.github.pvtitov.aichat.mcpserver;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class KnowledgeRepository {

    private static final String DB_PATH = "knowledge.db";
    private static Connection connection;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            connection.setAutoCommit(true);
            createTableIfNotExists();
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException("Failed to initialize knowledge database: " + e.getMessage(), e);
        }
    }

    private static void createTableIfNotExists() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS knowledge (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                     "title TEXT NOT NULL UNIQUE, " +
                     "description TEXT NOT NULL, " +
                     "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Save or update a knowledge entry
     */
    public static boolean saveKnowledge(String title, String description) {
        String sql = "INSERT INTO knowledge (title, description) VALUES (?, ?) " +
                     "ON CONFLICT(title) DO UPDATE SET description = excluded.description";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, title);
            pstmt.setString(2, description);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error saving knowledge: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get all knowledge titles, optionally filtered by regex
     */
    public static List<String> getKnowledgeContents(String regex) {
        List<String> titles = new ArrayList<>();
        String sql = "SELECT title FROM knowledge ORDER BY title";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String title = rs.getString("title");
                if (regex == null || regex.isEmpty()) {
                    titles.add(title);
                } else {
                    try {
                        if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(title).find()) {
                            titles.add(title);
                        }
                    } catch (Exception e) {
                        // Invalid regex, skip
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving knowledge contents: " + e.getMessage());
        }
        return titles;
    }

    /**
     * Find knowledge entries matching regex, return title and description
     */
    public static List<KnowledgeEntry> findKnowledge(String regex) {
        List<KnowledgeEntry> entries = new ArrayList<>();
        String sql = "SELECT title, description FROM knowledge ORDER BY title";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String title = rs.getString("title");
                try {
                    if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(title).find()) {
                        String description = rs.getString("description");
                        entries.add(new KnowledgeEntry(title, description));
                    }
                } catch (Exception e) {
                    // Invalid regex, skip
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding knowledge: " + e.getMessage());
        }
        return entries;
    }

    public static class KnowledgeEntry {
        private final String title;
        private final String description;

        public KnowledgeEntry(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return "## " + title + "\n\n" + description;
        }
    }
}
