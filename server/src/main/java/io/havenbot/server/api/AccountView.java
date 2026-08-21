package io.havenbot.server.api;

import io.havenbot.server.model.AccountRecord;

import java.time.Instant;
import java.util.UUID;

public record AccountView(
        UUID id,
        String name,
        String username,
        String characterName,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountView from(AccountRecord record) {
        return new AccountView(
                record.id(),
                record.name(),
                record.username(),
                record.characterName(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
