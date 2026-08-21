package io.havenbot.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LiveFeedServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LiveFeedService service = new LiveFeedService(mock(ScreenshotService.class), objectMapper);

    @Test
    void publishesAndClearsLatestFrame() {
        UUID botId = UUID.randomUUID();
        byte[] content = "jpeg-frame".getBytes(StandardCharsets.UTF_8);
        Instant createdAt = Instant.parse("2026-08-21T04:00:00Z");
        var metadata = objectMapper.createObjectNode()
                .put("mediaType", "image/jpeg")
                .put("createdAt", createdAt.toString())
                .put("width", 1280)
                .put("height", 720);

        LiveFeedService.LiveFrame frame = service.publishFrame(
                botId,
                metadata,
                Base64.getEncoder().encodeToString(content)
        );

        assertArrayEquals(content, frame.content());
        assertEquals("image/jpeg", frame.mediaType());
        assertEquals(createdAt, frame.createdAt());
        assertEquals(1280, frame.width());
        assertEquals(720, frame.height());
        assertTrue(service.latestFrame(botId).isPresent());

        service.clearBot(botId);

        assertFalse(service.latestFrame(botId).isPresent());
    }

    @Test
    void rejectsInvalidBase64FrameContent() {
        UUID botId = UUID.randomUUID();
        var metadata = objectMapper.createObjectNode().put("mediaType", "image/jpeg");

        assertThrows(IllegalArgumentException.class, () -> service.publishFrame(botId, metadata, "not-base64"));
    }
}
