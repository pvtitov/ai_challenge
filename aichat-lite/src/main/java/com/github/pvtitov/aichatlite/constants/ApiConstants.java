package com.github.pvtitov.aichatlite.constants;

public final class ApiConstants {
    
    private ApiConstants() {
        throw new UnsupportedOperationException("Constants class");
    }

    public static final String GIGA_CHAT_AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    public static final String GIGA_CHAT_API_URL = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions";
    public static final long TOKEN_EXPIRATION_MS = 1800000; // 30 minutes
    public static final String GIGACHAT_API_CREDENTIALS_ENV = "GIGACHAT_API_CREDENTIALS";
    
    public static final String OLLAMA_URL = "http://localhost:11434";
    public static final String OLLAMA_MODEL = "nomic-embed-text";
    
    public static final String EMBEDDING_DB_PATH = "embeddings.db";
    
    // System prompt for the 1st LLM call - task decision
    public static final String TASK_DECISION_SYSTEM_PROMPT =
        "You are a task decision assistant. Analyze the user's current request and determine:\n" +
        "1. What task the user is asking about (taskTitle)\n" +
        "2. Whether this is a completely new task or a continuation/modification of an existing task (isNewTask)\n" +
        "3. If it's an existing task, provide the existing task ID (existingTaskId), otherwise null\n" +
        "4. Extract ALL current requirements for this task (requirements array)\n" +
        "5. Identify any NEW requirements that were just added or changed (addedRequirements array)\n\n" +
        "Rules:\n" +
        "- If the user is asking about a completely different task, set isNewTask=true and existingTaskId=null\n" +
        "- If the user is clarifying, modifying, or continuing a previous task, set isNewTask=false and provide existingTaskId\n" +
        "- If the previous task was completed, treat the next request as a new task even if related\n" +
        "- Requirements should include ALL essential values, terms, constraints, and clarifications\n" +
        "- addedRequirements should only contain requirements that are NEW or CHANGED in this request\n\n" +
        "Always respond in JSON format with the following structure:\n" +
        "{\n" +
        "  \"taskTitle\": \"precise title of the task\",\n" +
        "  \"isNewTask\": true/false,\n" +
        "  \"existingTaskId\": 123 or null,\n" +
        "  \"requirements\": [\"requirement 1\", \"requirement 2\"],\n" +
        "  \"addedRequirements\": [\"new requirement 1\"]\n" +
        "}\n\n" +
        "Return ONLY valid JSON without any markdown formatting or additional text.";

    // System prompt for the 2nd LLM call - answering user's question with task context
    public static final String ANSWER_SYSTEM_PROMPT =
        "You are a helpful assistant. Answer the user's question thoroughly and helpfully.\n\n" +
        "IMPORTANT: You have a CURRENT TASK that you need to focus on. This task is HIGHER PRIORITY than the conversation history. " +
        "Make sure your answer directly addresses the current task and its requirements.\n\n" +
        "You also have access to the conversation history for additional context, but the CURRENT TASK should be your primary focus.\n" +
        "Provide clear, accurate, and detailed responses that satisfy the current task requirements.";

    // System prompt for the 3rd LLM call - task completion status
    public static final String TASK_COMPLETION_SYSTEM_PROMPT =
        "You are a task completion evaluator. Analyze whether the assistant's answer sufficiently completed the user's task.\n\n" +
        "Evaluate based on:\n" +
        "1. Does the answer address all requirements?\n" +
        "2. Is the solution complete and accurate?\n\n" +
        "Rules:\n" +
        "- Set isCompleted=true ONLY if the answer is a sufficient solution to the task\n" +
        "- The reason should explain why the task is completed or not\n" +
        "- If not completed, provide specific instructions on what is missing and how to complete it\n\n" +
        "Always respond in JSON format with the following structure:\n" +
        "{\n" +
        "  \"isCompleted\": true/false,\n" +
        "  \"reason\": \"explanation of completion status and next steps if needed\"\n" +
        "}\n\n" +
        "Return ONLY valid JSON without any markdown formatting or additional text.";
}
