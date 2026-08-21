package io.havenbot.protocol;

public record PositionSnapshot(
        Double x,
        Double y,
        String gridId
) {
}

