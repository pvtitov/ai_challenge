package com.github.pvtitov.aichat.constants;

public final class DatabaseConstants {

    private DatabaseConstants() {
    }

    public static final String DB_URL = "jdbc:sqlite:aichat.db";
    public static final String CREATE_CHAT_HISTORY_TABLE = "CREATE TABLE IF NOT EXISTS chat_history (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "branch INTEGER NOT NULL," +
            "role TEXT NOT NULL," +
            "content TEXT NOT NULL," +
            "prompt_tokens INTEGER," +
            "completion_tokens INTEGER," +
            "total_tokens INTEGER," +
            "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";

}
