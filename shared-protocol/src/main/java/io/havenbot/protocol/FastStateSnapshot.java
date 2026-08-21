package io.havenbot.protocol;

import java.time.Instant;

public record FastStateSnapshot(
        String botId,
        String sessionStatus,
        PositionSnapshot position,
        HealthSnapshot health,
        MeterSnapshot stamina,
        MeterSnapshot energy,
        TaskSnapshot currentTask,
        Boolean automationPaused,
        Instant capturedAt
) {
}
