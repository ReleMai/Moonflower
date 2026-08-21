package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.server.model.MediaClipRecord;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MediaClipService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Path clipDir = Paths.get("../server-data/clips");

    public MediaClipService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) throws IOException {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        Files.createDirectories(clipDir);
    }

    public List<MediaClipRecord> list(UUID botId) {
        if (botId == null) {
            return jdbcTemplate.query("select * from media_clips order by created_at desc limit 200", mapper());
        }
        return jdbcTemplate.query("select * from media_clips where bot_id = ? order by created_at desc limit 200", mapper(), botId.toString());
    }

    public MediaClipRecord get(UUID id) {
        return jdbcTemplate.query("select * from media_clips where id = ?", mapper(), id.toString())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Clip not found."));
    }

    public MediaClipRecord register(UUID botId, JsonNode payload) {
        UUID id = UUID.fromString(payload.path("clipId").asText());
        String fileName = payload.path("fileName").asText();
        String mediaType = payload.path("mediaType").asText("video/mp4");
        String triggerType = payload.path("triggerType").asText("manual");
        String reason = payload.path("reason").asText("");
        int durationSeconds = payload.path("durationSeconds").asInt(0);
        Instant createdAt = parseInstant(payload.path("createdAt").asText(null));
        MediaClipRecord record = new MediaClipRecord(id, botId, fileName, mediaType, triggerType, reason, durationSeconds, payload, createdAt);
        jdbcTemplate.update(
                "insert into media_clips(id, bot_id, file_name, media_type, trigger_type, reason, duration_seconds, metadata_json, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                record.id().toString(),
                record.botId().toString(),
                record.fileName(),
                record.mediaType(),
                record.triggerType(),
                record.reason(),
                record.durationSeconds(),
                record.metadata().toString(),
                record.createdAt().toString()
        );
        return record;
    }

    public Resource load(MediaClipRecord record) {
        return new FileSystemResource(clipDir.resolve(record.fileName()));
    }

    private RowMapper<MediaClipRecord> mapper() {
        return (rs, rowNum) -> new MediaClipRecord(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("bot_id")),
                rs.getString("file_name"),
                rs.getString("media_type"),
                rs.getString("trigger_type"),
                rs.getString("reason"),
                rs.getInt("duration_seconds"),
                readJson(rs, "metadata_json"),
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

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ex) {
            return Instant.now();
        }
    }
}
