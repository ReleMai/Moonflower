package io.havenbot.protocol;

public record HealthSnapshot(
        Integer current,
        Integer max,
        Double percentage,
        String text,
        Integer shp,
        Integer hhp,
        Integer mhp,
        Double softPercentage,
        Double hardPercentage,
        String displayText
) {
}
