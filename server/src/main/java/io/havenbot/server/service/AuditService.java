package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.server.model.AuditRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void log(UUID botId, String actor, String eventType, JsonNode details) {
        jdbcTemplate.update(
                "insert into audit_events(bot_id, actor, event_type, details_json, created_at) values (?, ?, ?, ?, ?)",
                botId == null ? null : botId.toString(),
                actor,
                eventType,
                details == null ? "{}" : details.toString(),
                Instant.now().toString()
        );
    }

    public List<AuditRecord> list() {
        return jdbcTemplate.query("select * from audit_events order by created_at desc limit 200", mapper());
    }

    private RowMapper<AuditRecord> mapper() {
        return (rs, rowNum) -> new AuditRecord(
                rs.getLong("id"),
                rs.getString("bot_id") == null ? null : UUID.fromString(rs.getString("bot_id")),
                rs.getString("actor"),
                rs.getString("event_type"),
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
