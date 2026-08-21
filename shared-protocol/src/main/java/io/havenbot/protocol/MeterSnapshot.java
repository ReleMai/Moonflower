package io.havenbot.protocol;

public record MeterSnapshot(
        Integer current,
        Integer max,
        Double percentage,
        String text
) {
}

