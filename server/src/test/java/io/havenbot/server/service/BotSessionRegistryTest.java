package io.havenbot.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotSessionRegistryTest {
    @Test
    void launchTokensAreSingleUseAndBoundToBot() {
        BotSessionRegistry registry = new BotSessionRegistry(new ObjectMapper());
        UUID botId = UUID.randomUUID();
        String token = registry.issueLaunchToken(botId);

        assertTrue(registry.acceptLaunchToken(botId, token));
        assertFalse(registry.acceptLaunchToken(botId, token));
    }

    @Test
    void launchTokensRejectDifferentBots() {
        BotSessionRegistry registry = new BotSessionRegistry(new ObjectMapper());
        UUID botId = UUID.randomUUID();
        UUID otherBotId = UUID.randomUUID();
        String token = registry.issueLaunchToken(botId);

        assertFalse(registry.acceptLaunchToken(otherBotId, token));
        assertFalse(registry.acceptLaunchToken(botId, token));
    }

    @Test
    void markBotSeenTracksFreshHeartbeatState() {
        BotSessionRegistry registry = new BotSessionRegistry(new ObjectMapper());
        UUID botId = UUID.randomUUID();

        registry.markBotSeen(botId);

        assertTrue(registry.getLastSeen(botId).isPresent());
        assertFalse(registry.isStale(botId, Duration.ofSeconds(5)));
    }
}
