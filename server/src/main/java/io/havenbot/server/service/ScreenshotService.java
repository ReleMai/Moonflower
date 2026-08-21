package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.server.model.ScreenshotRecord;
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
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class ScreenshotService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Path screenshotDir = Paths.get("../server-data/screenshots");

    public ScreenshotService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) throws IOException {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        Files.createDirectories(screenshotDir);
        ensureSavedColumn();
    }

    public List<ScreenshotRecord> list(UUID botId) {
        if (botId == null) {
            return jdbcTemplate.query("select * from screenshots where saved = 1 order by created_at desc limit 200", mapper());
        }
        return jdbcTemplate.query("select * from screenshots where bot_id = ? and saved = 1 order by created_at desc limit 200", mapper(), botId.toString());
    }

    public ScreenshotRecord store(UUID botId, JsonNode metadata, String base64Content) {
        return storeSaved(botId, metadata, Base64.getDecoder().decode(base64Content));
    }

    public ScreenshotRecord storeSaved(UUID botId, JsonNode metadata, byte[] content) {
        return store(botId, metadata, content, true);
    }

    public ScreenshotRecord get(UUID id) {
        return jdbcTemplate.query("select * from screenshots where id = ?", mapper(), id.toString())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Screenshot not found."));
    }

    public Resource load(ScreenshotRecord record) {
        return new FileSystemResource(screenshotDir.resolve(record.fileName()));
    }

    public int purgeUnsaved(UUID botId) {
        List<ScreenshotRecord> records = jdbcTemplate.query("select * from screenshots where bot_id = ? and saved = 0", mapper(), botId.toString());
        records.forEach(this::deleteFileQuietly);
        jdbcTemplate.update("delete from screenshots where bot_id = ? and saved = 0", botId.toString());
        return records.size();
    }

    private ScreenshotRecord store(UUID botId, JsonNode metadata, byte[] content, boolean saved) {
        UUID id = UUID.randomUUID();
        String mediaType = metadata.path("mediaType").asText("image/jpeg");
        String fileName = id + extensionFor(mediaType);
        Path target = screenshotDir.resolve(fileName);
        try {
            Files.write(target, content);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save screenshot.", ex);
        }
        ScreenshotRecord record = new ScreenshotRecord(id, botId, fileName, mediaType, saved, metadata, Instant.now());
        jdbcTemplate.update(
                "insert into screenshots(id, bot_id, file_name, media_type, saved, metadata_json, created_at) values (?, ?, ?, ?, ?, ?, ?)",
                record.id().toString(),
                record.botId().toString(),
                record.fileName(),
                record.mediaType(),
                record.saved() ? 1 : 0,
                record.metadata().toString(),
                record.createdAt().toString()
        );
        return record;
    }

    private RowMapper<ScreenshotRecord> mapper() {
        return (rs, rowNum) -> new ScreenshotRecord(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("bot_id")),
                rs.getString("file_name"),
                rs.getString("media_type"),
                rs.getInt("saved") != 0,
                readJson(rs, "metadata_json"),
                Instant.parse(rs.getString("created_at"))
        );
    }

    private void ensureSavedColumn() {
        List<String> columns = jdbcTemplate.query("pragma table_info(screenshots)", (rs, rowNum) -> rs.getString("name"));
        if (!columns.contains("saved")) {
            jdbcTemplate.execute("alter table screenshots add column saved integer not null default 1");
        }
    }

    private void deleteFileQuietly(ScreenshotRecord record) {
        try {
            Files.deleteIfExists(screenshotDir.resolve(record.fileName()));
        } catch (IOException ignored) {
        }
    }

    private String extensionFor(String mediaType) {
        return switch (mediaType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private JsonNode readJson(ResultSet rs, String column) throws SQLException {
        try {
            return objectMapper.readTree(rs.getString(column));
        } catch (IOException ex) {
            throw new SQLException("Failed to parse JSON column " + column, ex);
        }
    }
}
