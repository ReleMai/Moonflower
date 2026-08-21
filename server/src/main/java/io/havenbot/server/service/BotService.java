package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.protocol.BotStatus;
import io.havenbot.server.model.BotRecord;
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
public class BotService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BotService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<BotRecord> list() {
        return jdbcTemplate.query("select * from bots order by created_at desc", mapper());
    }

    public Optional<BotRecord> get(UUID id) {
        return jdbcTemplate.query("select * from bots where id = ?", mapper(), id.toString()).stream().findFirst();
    }

    public BotRecord create(String name, UUID accountId, String clientInstallPath, String preferredCharacter, String preferredWorld,
                            String profileName, String launchCommand) {
        Instant now = Instant.now();
        BotRecord record = new BotRecord(
                UUID.randomUUID(),
                name,
                accountId,
                clientInstallPath,
                preferredCharacter,
                preferredWorld,
                profileName,
                launchCommand,
                BotStatus.OFFLINE,
                null,
                false,
                null,
                now,
                now
        );
        jdbcTemplate.update(
                "insert into bots(id, name, account_id, client_install_path, preferred_character, preferred_world, profile_name, launch_command, status, registration_secret, takeover_active, last_state_json, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                record.id().toString(),
                record.name(),
                record.accountId() == null ? null : record.accountId().toString(),
                record.clientInstallPath(),
                record.preferredCharacter(),
                record.preferredWorld(),
                record.profileName(),
                record.launchCommand(),
                record.status().name(),
                null,
                0,
                null,
                record.createdAt().toString(),
                record.updatedAt().toString()
        );
        return record;
    }

    public BotRecord update(UUID id, String name, UUID accountId, String clientInstallPath, String preferredCharacter,
                            String preferredWorld, String profileName, String launchCommand) {
        BotRecord existing = get(id).orElseThrow(() -> new IllegalArgumentException("Bot not found."));
        BotRecord updated = new BotRecord(
                existing.id(),
                name,
                accountId,
                clientInstallPath,
                preferredCharacter,
                preferredWorld,
                profileName,
                launchCommand,
                existing.status(),
                existing.registrationSecret(),
                existing.takeoverActive(),
                existing.lastState(),
                existing.createdAt(),
                Instant.now()
        );
        jdbcTemplate.update(
                "update bots set name = ?, account_id = ?, client_install_path = ?, preferred_character = ?, preferred_world = ?, profile_name = ?, launch_command = ?, updated_at = ? where id = ?",
                updated.name(),
                updated.accountId() == null ? null : updated.accountId().toString(),
                updated.clientInstallPath(),
                updated.preferredCharacter(),
                updated.preferredWorld(),
                updated.profileName(),
                updated.launchCommand(),
                updated.updatedAt().toString(),
                updated.id().toString()
        );
        return updated;
    }

    public void delete(UUID id) {
        jdbcTemplate.update("delete from bots where id = ?", id.toString());
    }

    public void updateStatus(UUID id, BotStatus status) {
        jdbcTemplate.update("update bots set status = ?, updated_at = ? where id = ?", status.name(), Instant.now().toString(), id.toString());
    }

    public void setRegistrationSecret(UUID id, String secret) {
        jdbcTemplate.update("update bots set registration_secret = ?, updated_at = ? where id = ?", secret, Instant.now().toString(), id.toString());
    }

    public void setTakeover(UUID id, boolean active) {
        jdbcTemplate.update("update bots set takeover_active = ?, updated_at = ? where id = ?", active ? 1 : 0, Instant.now().toString(), id.toString());
    }

    public void updateState(UUID id, JsonNode state, BotStatus status) {
        jdbcTemplate.update(
                "update bots set last_state_json = ?, status = ?, updated_at = ? where id = ?",
                state == null ? null : state.toString(),
                status.name(),
                Instant.now().toString(),
                id.toString()
        );
    }

    private RowMapper<BotRecord> mapper() {
        return (rs, rowNum) -> new BotRecord(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("account_id") == null ? null : UUID.fromString(rs.getString("account_id")),
                rs.getString("client_install_path"),
                rs.getString("preferred_character"),
                rs.getString("preferred_world"),
                rs.getString("profile_name"),
                rs.getString("launch_command"),
                BotStatus.valueOf(rs.getString("status")),
                rs.getString("registration_secret"),
                rs.getInt("takeover_active") == 1,
                readJson(rs, "last_state_json"),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at"))
        );
    }

    private JsonNode readJson(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (IOException ex) {
            throw new SQLException("Failed to parse JSON column " + column, ex);
        }
    }
}

