package io.havenbot.server.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.havenbot.protocol.BotStatus;

import java.time.Instant;
import java.util.UUID;

public record BotRecord(
        UUID id,
        String name,
        UUID accountId,
        String clientInstallPath,
        String preferredCharacter,
        String preferredWorld,
        String profileName,
        String launchCommand,
        BotStatus status,
        String registrationSecret,
        boolean takeoverActive,
        JsonNode lastState,
        Instant createdAt,
        Instant updatedAt
) {
}

