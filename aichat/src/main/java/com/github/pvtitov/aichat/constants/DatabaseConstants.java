package com.github.pvtitov.aichat.constants;

public final class DatabaseConstants {

    private DatabaseConstants() {
    }

    public static final String DB_URL = "jdbc:sqlite:aichat.db";
    public static final String CHAT_HISTORY_SHORT_TERM_TABLE = "chat_history_short_term";
    public static final String CHAT_HISTORY_MID_TERM_TABLE = "chat_history_mid_term";
    public static final String CHAT_HISTORY_LONG_TERM_TABLE = "chat_history_long_term";

    public static final String CREATE_CHAT_HISTORY_SHORT_TERM_TABLE = "CREATE TABLE IF NOT EXISTS " + CHAT_HISTORY_SHORT_TERM_TABLE + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "branch INTEGER NOT NULL," +
            "role TEXT NOT NULL," +
            "content TEXT NOT NULL," +
            "prompt_tokens INTEGER," +
            "completion_tokens INTEGER," +
            "total_tokens INTEGER," +
            "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";

    public static final String CREATE_CHAT_HISTORY_MID_TERM_TABLE = "CREATE TABLE IF NOT EXISTS " + CHAT_HISTORY_MID_TERM_TABLE + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "branch INTEGER NOT NULL," +
            "role TEXT NOT NULL," +
            "content TEXT NOT NULL," +
            "prompt_tokens INTEGER," +
            "completion_tokens INTEGER," +
            "total_tokens INTEGER," +
            "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";

    public static final String CREATE_CHAT_HISTORY_LONG_TERM_TABLE = "CREATE TABLE IF NOT EXISTS " + CHAT_HISTORY_LONG_TERM_TABLE + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "branch INTEGER NOT NULL," +
            "role TEXT NOT NULL," +
            "content TEXT NOT NULL," +
            "prompt_tokens INTEGER," +
            "completion_tokens INTEGER," +
            "total_tokens INTEGER," +
            "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";
}
