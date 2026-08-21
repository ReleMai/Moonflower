package io.havenbot.protocol;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record BotCommandEnvelope(
        String commandId,
        String botId,
        BotCommandType type,
        JsonNode payload,
        Instant issuedAt
) {
}

