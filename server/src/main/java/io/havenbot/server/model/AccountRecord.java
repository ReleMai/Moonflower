package io.havenbot.server.model;

import java.time.Instant;
import java.util.UUID;

public record AccountRecord(
        UUID id,
        String name,
        String username,
        String encryptedSecret,
        String characterName,
        Instant createdAt,
        Instant updatedAt
) {
}

