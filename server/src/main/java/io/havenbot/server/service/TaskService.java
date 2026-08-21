package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.havenbot.protocol.TaskStatus;
import io.havenbot.server.model.TaskRecord;
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
public class TaskService {
    private final JdbcTemplate jdbcTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public TaskService(JdbcTemplate jdbcTemplate, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<TaskRecord> list() {
        return jdbcTemplate.query("select * from tasks order by queued_at desc limit 200", mapper());
    }

    public List<TaskRecord> listForBot(UUID botId) {
        return jdbcTemplate.query("select * from tasks where bot_id = ? order by queued_at desc limit 200", mapper(), botId.toString());
    }

    public Optional<TaskRecord> get(UUID taskId) {
        return jdbcTemplate.query("select * from tasks where id = ?", mapper(), taskId.toString()).stream().findFirst();
    }

    public TaskRecord enqueue(UUID botId, String actionType, JsonNode params) {
        TaskRecord record = new TaskRecord(UUID.randomUUID(), botId, actionType, params, TaskStatus.QUEUED, Instant.now(), null, null, null);
        jdbcTemplate.update(
                "insert into tasks(id, bot_id, action_type, params_json, status, queued_at, started_at, completed_at, error_message) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                record.id().toString(),
                record.botId().toString(),
                record.actionType(),
                record.params().toString(),
                record.status().name(),
                record.queuedAt().toString(),
                null,
                null,
                null
        );
        return record;
    }

    public Optional<TaskRecord> nextQueued(UUID botId) {
        return jdbcTemplate.query(
                "select * from tasks where bot_id = ? and status = ? order by queued_at asc limit 1",
                mapper(),
                botId.toString(),
                TaskStatus.QUEUED.name()
        ).stream().findFirst();
    }

    public void markDispatched(UUID taskId) {
        jdbcTemplate.update("update tasks set status = ? where id = ?", TaskStatus.DISPATCHED.name(), taskId.toString());
    }

    public void markStarted(UUID taskId) {
        jdbcTemplate.update("update tasks set status = ?, started_at = ? where id = ?", TaskStatus.RUNNING.name(), Instant.now().toString(), taskId.toString());
    }

    public void markCompleted(UUID taskId) {
        jdbcTemplate.update("update tasks set status = ?, completed_at = ?, error_message = null where id = ?", TaskStatus.COMPLETED.name(), Instant.now().toString(), taskId.toString());
    }

    public void markFailed(UUID taskId, String message) {
        jdbcTemplate.update("update tasks set status = ?, completed_at = ?, error_message = ? where id = ?", TaskStatus.FAILED.name(), Instant.now().toString(), message, taskId.toString());
    }

    public void markInterruptedForBot(UUID botId, String message) {
        jdbcTemplate.update(
                "update tasks set status = ?, completed_at = ?, error_message = ? where bot_id = ? and status in (?, ?, ?)",
                TaskStatus.INTERRUPTED.name(),
                Instant.now().toString(),
                message,
                botId.toString(),
                TaskStatus.QUEUED.name(),
                TaskStatus.DISPATCHED.name(),
                TaskStatus.RUNNING.name()
        );
    }

    public void markActiveInterruptedForBot(UUID botId, String message) {
        jdbcTemplate.update(
                "update tasks set status = ?, completed_at = ?, error_message = ? where bot_id = ? and status in (?, ?)",
                TaskStatus.INTERRUPTED.name(),
                Instant.now().toString(),
                message,
                botId.toString(),
                TaskStatus.DISPATCHED.name(),
                TaskStatus.RUNNING.name()
        );
    }

    public int cancelQueuedForBot(UUID botId, String message) {
        return jdbcTemplate.update(
                "update tasks set status = ?, completed_at = ?, error_message = ? where bot_id = ? and status = ?",
                TaskStatus.CANCELED.name(),
                Instant.now().toString(),
                message,
                botId.toString(),
                TaskStatus.QUEUED.name()
        );
    }

    public void markCanceled(UUID taskId, String message) {
        jdbcTemplate.update(
                "update tasks set status = ?, completed_at = ?, error_message = ? where id = ? and status in (?, ?, ?)",
                TaskStatus.CANCELED.name(),
                Instant.now().toString(),
                message,
                taskId.toString(),
                TaskStatus.QUEUED.name(),
                TaskStatus.DISPATCHED.name(),
                TaskStatus.RUNNING.name()
        );
    }

    private RowMapper<TaskRecord> mapper() {
        return (rs, rowNum) -> new TaskRecord(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("bot_id")),
                rs.getString("action_type"),
                readJson(rs, "params_json"),
                TaskStatus.valueOf(rs.getString("status")),
                Instant.parse(rs.getString("queued_at")),
                rs.getString("started_at") == null ? null : Instant.parse(rs.getString("started_at")),
                rs.getString("completed_at") == null ? null : Instant.parse(rs.getString("completed_at")),
                rs.getString("error_message")
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
