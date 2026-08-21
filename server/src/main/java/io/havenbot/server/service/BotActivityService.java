package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.server.model.BotActivityRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BotActivityService {
    private static final int DEFAULT_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BotActivityService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public BotActivityRecord log(UUID botId, String source, String category, String message, JsonNode details) {
        Instant createdAt = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into bot_activity_events(bot_id, source, category, message, details_json, created_at) values (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, botId.toString());
            statement.setString(2, source);
            statement.setString(3, category);
            statement.setString(4, message);
            statement.setString(5, details == null ? "{}" : details.toString());
            statement.setString(6, createdAt.toString());
            return statement;
        }, keyHolder);
        long id = keyHolder.getKey() == null ? -1L : keyHolder.getKey().longValue();
        return new BotActivityRecord(id, botId, source, category, message, details, createdAt);
    }

    public List<BotActivityRecord> listForBot(UUID botId, Integer limit) {
        int boundedLimit = Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, 500));
        return jdbcTemplate.query(
                "select * from bot_activity_events where bot_id = ? order by created_at desc limit ?",
                mapper(),
                botId.toString(),
                boundedLimit
        );
    }

    private RowMapper<BotActivityRecord> mapper() {
        return (rs, rowNum) -> new BotActivityRecord(
                rs.getLong("id"),
                UUID.fromString(rs.getString("bot_id")),
                rs.getString("source"),
                rs.getString("category"),
                rs.getString("message"),
                readJson(rs, "details_json"),
                Instant.parse(rs.getString("created_at"))
        );
    }

    private JsonNode readJson(ResultSet rs, String column) throws SQLException {
        try {
            return objectMapper.readTree(rs.getString(column));
        } catch (IOException ex) {
            throw new SQLException("Failed to parse JSON column " + column, ex);
        }
    }
}
