package io.havenbot.protocol;

import java.time.Instant;

public record TaskSnapshot(
        String taskId,
        String actionType,
        TaskStatus status,
        String message,
        Instant startedAt,
        Instant updatedAt
) {
}

