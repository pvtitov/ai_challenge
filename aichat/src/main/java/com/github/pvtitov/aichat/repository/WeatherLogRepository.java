package com.github.pvtitov.aichat.repository;

import com.github.pvtitov.aichat.model.WeatherLog;

import java.util.List;

public interface WeatherLogRepository {
    void save(WeatherLog weatherLog);
    List<WeatherLog> findRecent(String profileLogin, int limit);
    void deleteAll(String profileLogin);
}
