package io.havenbot.server.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record RoutePresetRecord(
        UUID id,
        String name,
        JsonNode route,
        Instant createdAt
) {
}

