package io.havenbot.protocol;

import java.time.Instant;

public record BotRegistrationPayload(
        String botId,
        String registrationToken,
        String clientVersion,
        String characterName,
        String worldName,
        Instant registeredAt
) {
}

