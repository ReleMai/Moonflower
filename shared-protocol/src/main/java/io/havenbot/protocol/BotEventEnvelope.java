package io.havenbot.protocol;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record BotEventEnvelope(
        String eventId,
        String botId,
        BotEventType type,
        JsonNode payload,
        Instant createdAt
) {
}

