package io.havenbot.server.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ScreenshotRecord(
        UUID id,
        UUID botId,
        String fileName,
        String mediaType,
        boolean saved,
        JsonNode metadata,
        Instant createdAt
) {
}
