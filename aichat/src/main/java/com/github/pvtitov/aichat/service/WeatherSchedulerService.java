package com.github.pvtitov.aichat.service;

import com.github.pvtitov.aichat.model.WeatherLog;
import com.github.pvtitov.aichat.repository.WeatherLogRepository;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class WeatherSchedulerService {

    private final McpService mcpService;
    private final WeatherLogRepository weatherLogRepository;
    private final TaskScheduler taskScheduler;

    // Per-profile scheduling: profileLogin -> ScheduledFuture
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, WeatherSchedulerConfig> schedulerConfigs = new ConcurrentHashMap<>();

    public WeatherSchedulerService(McpService mcpService, WeatherLogRepository weatherLogRepository) {
        this.mcpService = mcpService;
        this.weatherLogRepository = weatherLogRepository;
        this.taskScheduler = createTaskScheduler();
    }

    private TaskScheduler createTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("weather-scheduler-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    public void startWeatherScheduler(String profileLogin, String city, Duration period, int summaryCount) {
        // Stop any existing scheduler for this profile
        stopWeatherScheduler(profileLogin);

        // Ensure MCP connection
        if (!mcpService.isConnected()) {
            mcpService.initializeConnection();
        }

        // Save config for display
        schedulerConfigs.put(profileLogin, new WeatherSchedulerConfig(city, period, summaryCount));

        // Schedule the task
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                () -> fetchAndSaveWeather(profileLogin, city),
                Instant.now(),
                period
        );

        scheduledTasks.put(profileLogin, future);
    }

    public boolean stopWeatherScheduler(String profileLogin) {
        ScheduledFuture<?> future = scheduledTasks.remove(profileLogin);
        schedulerConfigs.remove(profileLogin);
        if (future != null) {
            future.cancel(false);
            return true;
        }
        return false;
    }

    public boolean isRunning(String profileLogin) {
        return scheduledTasks.containsKey(profileLogin);
    }

    public WeatherSchedulerConfig getConfig(String profileLogin) {
        return schedulerConfigs.get(profileLogin);
    }

    private void fetchAndSaveWeather(String profileLogin, String city) {
        try {
            String result = mcpService.callTool("get_weather", Map.of("city", city));
            if (result != null && !result.startsWith("Error:")) {
                WeatherLog log = new WeatherLog(city, result, profileLogin);
                weatherLogRepository.save(log);
            }
        } catch (Exception e) {
            System.err.println("Weather fetch failed for " + city + ": " + e.getMessage());
        }
    }

    public static class WeatherSchedulerConfig {
        private final String city;
        private final Duration period;
        private final int summaryCount;

        public WeatherSchedulerConfig(String city, Duration period, int summaryCount) {
            this.city = city;
            this.period = period;
            this.summaryCount = summaryCount;
        }

        public String getCity() {
            return city;
        }

        public Duration getPeriod() {
            return period;
        }

        public int getSummaryCount() {
            return summaryCount;
        }

        public String getPeriodDescription() {
            long seconds = period.getSeconds();
            if (seconds < 60) {
                return seconds + "s";
            } else if (seconds < 3600) {
                return (seconds / 60) + "m";
            } else if (seconds < 86400) {
                return (seconds / 3600) + "h";
            } else {
                return (seconds / 86400) + "d";
            }
        }
    }
}
