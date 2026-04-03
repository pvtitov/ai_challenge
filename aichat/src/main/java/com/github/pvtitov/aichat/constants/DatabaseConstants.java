package com.github.pvtitov.aichat.constants;

public final class DatabaseConstants {

    private DatabaseConstants() {
    }

    public static final String DB_URL = "jdbc:sqlite:aichat.db";
    public static final String PROFILE_TABLE = "profiles";
    public static final String CHAT_HISTORY_SHORT_TERM_TABLE = "chat_history_short_term";
    public static final String CHAT_HISTORY_MID_TERM_TABLE = "chat_history_mid_term";
    public static final String CHAT_HISTORY_LONG_TERM_TABLE = "chat_history_long_term";

    public static final String INVARIANTS_TABLE = "invariants";
    public static final String INVARIANTS_ID = "id";
    public static final String INVARIANTS_TEXT = "text";
    public static final String INVARIANTS_BRANCH = "branch";
    public static final String INVARIANTS_PROFILE_LOGIN = "profile_login";

    public static final String CREATE_PROFILE_TABLE = "CREATE TABLE IF NOT EXISTS " + PROFILE_TABLE + " (" +
            "login TEXT PRIMARY KEY," +
            "profile_data TEXT NOT NULL," +
            "current_branch INTEGER DEFAULT 1)";

    public static final String CREATE_CHAT_HISTORY_SHORT_TERM_TABLE = "CREATE TABLE IF NOT EXISTS " + CHAT_HISTORY_SHORT_TERM_TABLE + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "branch INTEGER NOT NULL," +
            "role TEXT NOT NULL," +
            "content TEXT NOT NULL," +
            "prompt_tokens INTEGER," +
            "completion_tokens INTEGER," +
            "total_tokens INTEGER," +
            "profile_login TEXT NOT NULL," +
            "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";

    public static final String CREATE_CHAT_HISTORY_MID_TERM_TABLE = "CREATE TABLE IF NOT EXISTS " + CHAT_HISTORY_MID_TERM_TABLE + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "branch INTEGER NOT NULL," +
            "role TEXT NOT NULL," +
            "content TEXT NOT NULL," +
            "prompt_tokens INTEGER," +
            "completion_tokens INTEGER," +
            "total_tokens INTEGER," +
            "profile_login TEXT NOT NULL," +
            "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";

    public static final String CREATE_CHAT_HISTORY_LONG_TERM_TABLE = "CREATE TABLE IF NOT EXISTS " + CHAT_HISTORY_LONG_TERM_TABLE + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "branch INTEGER NOT NULL," +
            "role TEXT NOT NULL," +
            "content TEXT NOT NULL," +
            "prompt_tokens INTEGER," +
            "completion_tokens INTEGER," +
            "total_tokens INTEGER," +
            "profile_login TEXT NOT NULL," +
            "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";
}
