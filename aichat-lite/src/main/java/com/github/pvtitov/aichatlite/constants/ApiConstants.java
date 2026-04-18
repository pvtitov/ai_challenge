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
    
    // Structured response format for LLM
    public static final String SYSTEM_PROMPT_TEMPLATE = 
        "You are a helpful assistant. Always respond in JSON format with the following structure:\n" +
        "{\n" +
        "  \"response\": \"your response to the user\",\n" +
        "  \"tasks\": [\n" +
        "    {\n" +
        "      \"title\": \"short but precise title or description of task\",\n" +
        "      \"requirements\": [\"requirement 1\", \"requirement 2\"],\n" +
        "      \"invariants\": [\"invariant 1\", \"invariant 2\"],\n" +
        "      \"verification\": {\n" +
        "        \"verified\": true,\n" +
        "        \"summary\": \"verification conclusion\"\n" +
        "      }\n" +
        "    }\n" +
        "  ]\n" +
        "}\n\n" +
        "Rules:\n" +
        "- The 'response' field should be your final answer to the user request.\n" +
        "- The 'tasks' array should contain new tasks based on the request context. " +
        "Only include new tasks which don't have saved analog yet. " +
        "Include all requirements and invariants that should persist.\n" +
        "- If there are existing tasks, verify if your response complies with them and set 'verified' accordingly.\n" +
        "- Return ONLY valid JSON without any markdown formatting or additional text.";
}
