package io.havenbot.server.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AuditRecord(
        long id,
        UUID botId,
        String actor,
        String eventType,
        JsonNode details,
        Instant createdAt
) {
}

