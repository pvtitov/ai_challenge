package com.github.pvtitov.aichat.repository;

import com.github.pvtitov.aichat.constants.DatabaseConstants;
import com.github.pvtitov.aichat.model.WeatherLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class WeatherLogRepositoryImpl implements WeatherLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public WeatherLogRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcTemplate.execute(DatabaseConstants.CREATE_WEATHER_LOGS_TABLE);
    }

    @Override
    public void save(WeatherLog weatherLog) {
        jdbcTemplate.update(
                "INSERT INTO " + DatabaseConstants.WEATHER_LOGS_TABLE +
                        " (city, weather_data, profile_login) VALUES (?, ?, ?)",
                weatherLog.getCity(),
                weatherLog.getWeatherData(),
                weatherLog.getProfileLogin()
        );
    }

    @Override
    public List<WeatherLog> findRecent(String profileLogin, int limit) {
        return jdbcTemplate.query(
                "SELECT id, city, weather_data, request_time, profile_login FROM " +
                        DatabaseConstants.WEATHER_LOGS_TABLE +
                        " WHERE profile_login = ? ORDER BY request_time DESC LIMIT ?",
                this::mapRow,
                profileLogin,
                limit
        );
    }

    @Override
    public void deleteAll(String profileLogin) {
        jdbcTemplate.update(
                "DELETE FROM " + DatabaseConstants.WEATHER_LOGS_TABLE + " WHERE profile_login = ?",
                profileLogin
        );
    }

    private WeatherLog mapRow(ResultSet rs, int rowNum) throws SQLException {
        WeatherLog log = new WeatherLog();
        log.setId(rs.getLong("id"));
        log.setCity(rs.getString("city"));
        log.setWeatherData(rs.getString("weather_data"));
        Timestamp timestamp = rs.getTimestamp("request_time");
        if (timestamp != null) {
            log.setRequestTime(timestamp.toLocalDateTime());
        }
        log.setProfileLogin(rs.getString("profile_login"));
        return log;
    }
}
