package io.havenbot.server.service;

import io.havenbot.server.model.AccountRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {
    private final JdbcTemplate jdbcTemplate;
    private final SecretProtectionService secretProtectionService;

    public AccountService(JdbcTemplate jdbcTemplate, SecretProtectionService secretProtectionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.secretProtectionService = secretProtectionService;
    }

    public List<AccountRecord> list() {
        return jdbcTemplate.query("select * from accounts order by created_at desc", mapper());
    }

    public Optional<AccountRecord> get(UUID id) {
        List<AccountRecord> results = jdbcTemplate.query("select * from accounts where id = ?", mapper(), id.toString());
        return results.stream().findFirst();
    }

    public AccountRecord create(String name, String username, String secret, String characterName) {
        Instant now = Instant.now();
        AccountRecord record = new AccountRecord(
                UUID.randomUUID(),
                name,
                username,
                secretProtectionService.protect(secret),
                characterName,
                now,
                now
        );
        jdbcTemplate.update(
                "insert into accounts(id, name, username, encrypted_secret, character_name, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                record.id().toString(),
                record.name(),
                record.username(),
                record.encryptedSecret(),
                record.characterName(),
                record.createdAt().toString(),
                record.updatedAt().toString()
        );
        return record;
    }

    public AccountRecord update(UUID id, String name, String username, String secret, String characterName) {
        AccountRecord existing = get(id).orElseThrow(() -> new IllegalArgumentException("Account not found."));
        AccountRecord updated = new AccountRecord(
                existing.id(),
                name,
                username,
                (secret == null || secret.isBlank()) ? existing.encryptedSecret() : secretProtectionService.protect(secret),
                characterName,
                existing.createdAt(),
                Instant.now()
        );
        jdbcTemplate.update(
                "update accounts set name = ?, username = ?, encrypted_secret = ?, character_name = ?, updated_at = ? where id = ?",
                updated.name(),
                updated.username(),
                updated.encryptedSecret(),
                updated.characterName(),
                updated.updatedAt().toString(),
                updated.id().toString()
        );
        return updated;
    }

    public void delete(UUID id) {
        jdbcTemplate.update("delete from accounts where id = ?", id.toString());
    }

    public String revealSecret(UUID id) {
        AccountRecord record = get(id).orElseThrow(() -> new IllegalArgumentException("Account not found."));
        return secretProtectionService.unprotect(record.encryptedSecret());
    }

    private RowMapper<AccountRecord> mapper() {
        return (rs, rowNum) -> new AccountRecord(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("username"),
                rs.getString("encrypted_secret"),
                rs.getString("character_name"),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at"))
        );
    }
}

