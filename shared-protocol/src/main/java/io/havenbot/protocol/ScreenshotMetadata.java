package io.havenbot.protocol;

import java.time.Instant;

public record ScreenshotMetadata(
        String screenshotId,
        String botId,
        String mediaType,
        String fileName,
        Integer width,
        Integer height,
        Instant createdAt
) {
}

