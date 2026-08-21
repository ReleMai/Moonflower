package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.server.model.RoutePresetRecord;
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
public class RoutePresetService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RoutePresetService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<RoutePresetRecord> list() {
        return jdbcTemplate.query("select * from route_presets order by created_at desc", mapper());
    }

    public java.util.Optional<RoutePresetRecord> get(UUID id) {
        return jdbcTemplate.query("select * from route_presets where id = ?", mapper(), id.toString()).stream().findFirst();
    }

    public RoutePresetRecord create(String name, JsonNode route) {
        RoutePresetRecord record = new RoutePresetRecord(UUID.randomUUID(), name, route, Instant.now());
        jdbcTemplate.update(
                "insert into route_presets(id, name, route_json, created_at) values (?, ?, ?, ?)",
                record.id().toString(),
                record.name(),
                record.route().toString(),
                record.createdAt().toString()
        );
        return record;
    }

    public RoutePresetRecord update(UUID id, String name, JsonNode route) {
        RoutePresetRecord existing = get(id).orElseThrow(() -> new IllegalArgumentException("Route preset not found."));
        RoutePresetRecord updated = new RoutePresetRecord(existing.id(), name, route, existing.createdAt());
        jdbcTemplate.update(
                "update route_presets set name = ?, route_json = ? where id = ?",
                updated.name(),
                updated.route().toString(),
                updated.id().toString()
        );
        return updated;
    }

    public void delete(UUID id) {
        jdbcTemplate.update("delete from route_presets where id = ?", id.toString());
    }

    private RowMapper<RoutePresetRecord> mapper() {
        return (rs, rowNum) -> new RoutePresetRecord(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                readJson(rs, "route_json"),
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
