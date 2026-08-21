package io.havenbot.protocol;

public record StatDetailSnapshot(
        String key,
        String label,
        Integer value,
        String icon,
        String resourceName,
        String wikiUrl,
        String summary,
        String kind,
        String wikiTitle,
        String wikiSection
) {
}
