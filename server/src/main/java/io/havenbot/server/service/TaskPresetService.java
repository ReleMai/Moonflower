package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.server.model.TaskPresetRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TaskPresetService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TaskPresetService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<TaskPresetRecord> list() {
        return jdbcTemplate.query("select * from task_presets order by created_at desc", mapper());
    }

    public Optional<TaskPresetRecord> get(UUID id) {
        return jdbcTemplate.query("select * from task_presets where id = ?", mapper(), id.toString()).stream().findFirst();
    }

    public TaskPresetRecord create(String name, String actionType, JsonNode params) {
        TaskPresetRecord record = new TaskPresetRecord(UUID.randomUUID(), name, actionType, params, Instant.now());
        jdbcTemplate.update(
                "insert into task_presets(id, name, action_type, params_json, created_at) values (?, ?, ?, ?, ?)",
                record.id().toString(),
                record.name(),
                record.actionType(),
                record.params().toString(),
                record.createdAt().toString()
        );
        return record;
    }

    public TaskPresetRecord update(UUID id, String name, String actionType, JsonNode params) {
        TaskPresetRecord existing = get(id).orElseThrow(() -> new IllegalArgumentException("Task preset not found."));
        TaskPresetRecord updated = new TaskPresetRecord(existing.id(), name, actionType, params, existing.createdAt());
        jdbcTemplate.update(
                "update task_presets set name = ?, action_type = ?, params_json = ? where id = ?",
                updated.name(),
                updated.actionType(),
                updated.params().toString(),
                updated.id().toString()
        );
        return updated;
    }

    public void delete(UUID id) {
        jdbcTemplate.update("delete from task_presets where id = ?", id.toString());
    }

    private RowMapper<TaskPresetRecord> mapper() {
        return (rs, rowNum) -> new TaskPresetRecord(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("action_type"),
                readJson(rs, "params_json"),
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
