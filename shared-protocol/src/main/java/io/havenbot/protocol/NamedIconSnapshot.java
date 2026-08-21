package io.havenbot.protocol;

public record NamedIconSnapshot(
        String key,
        String label,
        String icon,
        String resourceName,
        String wikiUrl,
        String summary,
        String kind,
        String wikiTitle,
        String wikiSection,
        Boolean pursuing,
        Integer slotIndex
) {
}
