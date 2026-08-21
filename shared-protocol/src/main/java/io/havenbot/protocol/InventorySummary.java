package io.havenbot.protocol;

public record InventorySummary(
        int itemCount,
        int freeSlots,
        int occupiedSlots
) {
}

