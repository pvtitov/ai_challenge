package com.github.pvtitov.aichat.config;

import com.github.pvtitov.aichat.constants.DatabaseConstants;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.function.Function;

@Configuration
public class AppConfig {

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(DatabaseConstants.DB_URL);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(DatabaseConstants.CREATE_CHAT_HISTORY_SHORT_TERM_TABLE);
        jdbcTemplate.execute(DatabaseConstants.CREATE_CHAT_HISTORY_MID_TERM_TABLE);
        jdbcTemplate.execute(DatabaseConstants.CREATE_CHAT_HISTORY_LONG_TERM_TABLE);
        jdbcTemplate.execute(DatabaseConstants.CREATE_PROFILE_TABLE);
        return jdbcTemplate;
    }

    /**
     * Factory function to create a new McpSyncClient for a given server URL.
     * This allows creating new client instances when reconnecting to MCP servers.
     */
    @Bean
    public Function<String, McpSyncClient> mcpClientFactory() {
        return serverUrl -> {
            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(serverUrl).build();
            return McpClient.sync(transport).build();
        };
    }
}
