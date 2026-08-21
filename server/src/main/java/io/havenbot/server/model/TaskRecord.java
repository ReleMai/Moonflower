package io.havenbot.server.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.havenbot.protocol.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskRecord(
        UUID id,
        UUID botId,
        String actionType,
        JsonNode params,
        TaskStatus status,
        Instant queuedAt,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
}

