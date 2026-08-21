package io.havenbot.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class DataSourceConfig {
    @Bean
    public DataSource dataSource(@Value("${spring.datasource.url}") String jdbcUrl) {
        ensureSqliteDirectory(jdbcUrl);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTestQuery("select 1");
        return new HikariDataSource(config);
    }

    private void ensureSqliteDirectory(String jdbcUrl) {
        String prefix = "jdbc:sqlite:";
        if (!jdbcUrl.startsWith(prefix)) {
            return;
        }
        String pathValue = jdbcUrl.substring(prefix.length());
        if (pathValue.isBlank() || ":memory:".equals(pathValue)) {
            return;
        }
        Path path = Paths.get(pathValue).toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite directory " + parent, ex);
        }
    }
}
