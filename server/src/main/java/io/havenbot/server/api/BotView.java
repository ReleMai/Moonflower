package io.havenbot.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.havenbot.protocol.BotStatus;
import io.havenbot.server.model.BotRecord;

import java.time.Instant;
import java.util.UUID;

public record BotView(
        UUID id,
        String name,
        UUID accountId,
        String clientInstallPath,
        String preferredCharacter,
        String preferredWorld,
        String profileName,
        String launchCommand,
        BotStatus status,
        boolean takeoverActive,
        JsonNode lastState,
        Instant createdAt,
        Instant updatedAt
) {
    public static BotView from(BotRecord record) {
        return new BotView(
                record.id(),
                record.name(),
                record.accountId(),
                record.clientInstallPath(),
                record.preferredCharacter(),
                record.preferredWorld(),
                record.profileName(),
                record.launchCommand(),
                record.status(),
                record.takeoverActive(),
                record.lastState(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
