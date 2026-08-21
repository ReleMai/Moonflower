package io.havenbot.server.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record TaskPresetRecord(
        UUID id,
        String name,
        String actionType,
        JsonNode params,
        Instant createdAt
) {
}
