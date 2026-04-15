package com.github.pvtitov.aichat.config;

import com.github.pvtitov.aichat.constants.DatabaseConstants;
import com.github.pvtitov.aichat.repository.EmbeddingRepository;
import com.github.pvtitov.aichat.service.EmbeddingSearchService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.function.Function;

@Configuration
public class AppConfig {

    @Value("${embedding.db.path:embeddings.db}")
    private String embeddingDbPath;

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

    /**
     * Creates and configures the EmbeddingRepository bean for accessing saved embeddings.
     */
    @Bean
    public EmbeddingRepository embeddingRepository() {
        EmbeddingRepository repository = new EmbeddingRepository(embeddingDbPath);
        try {
            repository.initialize();
            System.out.println("EmbeddingRepository initialized with db path: " + embeddingDbPath);
        } catch (SQLException e) {
            System.err.println("Failed to initialize EmbeddingRepository: " + e.getMessage());
        }
        return repository;
    }

    /**
     * Creates and configures the EmbeddingSearchService bean for semantic search over embeddings.
     */
    @Bean
    public EmbeddingSearchService embeddingSearchService(EmbeddingRepository embeddingRepository) {
        EmbeddingSearchService service = new EmbeddingSearchService();
        service.setEmbeddingRepository(embeddingRepository);
        return service;
    }
}
