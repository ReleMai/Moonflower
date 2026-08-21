package io.havenbot.server.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record BotActivityRecord(
        long id,
        UUID botId,
        String source,
        String category,
        String message,
        JsonNode details,
        Instant createdAt
) {
}
