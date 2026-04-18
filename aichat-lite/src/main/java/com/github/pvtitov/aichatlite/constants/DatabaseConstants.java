package com.github.pvtitov.aichatlite.constants;

public final class DatabaseConstants {
    
    private DatabaseConstants() {
        throw new UnsupportedOperationException("Constants class");
    }

    public static final String DB_URL = "jdbc:sqlite:aichat-lite.db";
    
    public static final String CREATE_DIALOG_HISTORY_TABLE = 
        "CREATE TABLE IF NOT EXISTS dialog_history (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "role TEXT NOT NULL, " +
        "content TEXT NOT NULL, " +
        "prompt_tokens INTEGER, " +
        "completion_tokens INTEGER, " +
        "total_tokens INTEGER, " +
        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
        ")";
    
    public static final String CREATE_TASKS_TABLE = 
        "CREATE TABLE IF NOT EXISTS tasks (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "dialog_message_id INTEGER, " +
        "title TEXT NOT NULL, " +
        "requirements TEXT, " +
        "invariants TEXT, " +
        "verified BOOLEAN DEFAULT 1, " +
        "verification_summary TEXT, " +
        "FOREIGN KEY (dialog_message_id) REFERENCES dialog_history(id)" +
        ")";
}
