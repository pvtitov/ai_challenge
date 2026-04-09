package com.github.pvtitov.aichat.model;

import java.time.LocalDateTime;

public class WeatherLog {
    private Long id;
    private String city;
    private String weatherData;
    private LocalDateTime requestTime;
    private String profileLogin;

    public WeatherLog() {
    }

    public WeatherLog(String city, String weatherData, String profileLogin) {
        this.city = city;
        this.weatherData = weatherData;
        this.profileLogin = profileLogin;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getWeatherData() {
        return weatherData;
    }

    public void setWeatherData(String weatherData) {
        this.weatherData = weatherData;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }

    public String getProfileLogin() {
        return profileLogin;
    }

    public void setProfileLogin(String profileLogin) {
        this.profileLogin = profileLogin;
    }
}
