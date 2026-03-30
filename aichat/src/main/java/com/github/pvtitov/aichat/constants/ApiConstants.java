package com.github.pvtitov.aichat.constants;

public final class ApiConstants {

    private ApiConstants() {
    }

    public static final String GIGA_CHAT_AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    public static final String GIGA_CHAT_API_URL = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions";
    public static final long TOKEN_EXPIRATION_MS = 1800000; // 30 minutes
    public static final String GIGACHAT_API_CREDENTIALS_ENV = "GIGACHAT_API_CREDENTIALS";

}
