package io.havenbot.server.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record MediaClipRecord(
        UUID id,
        UUID botId,
        String fileName,
        String mediaType,
        String triggerType,
        String reason,
        int durationSeconds,
        JsonNode metadata,
        Instant createdAt
) {
}
