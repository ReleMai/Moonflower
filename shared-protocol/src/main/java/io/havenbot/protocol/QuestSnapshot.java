package io.havenbot.protocol;

import java.util.List;

public record QuestSnapshot(
        Integer id,
        String title,
        Integer done,
        Integer mtime,
        String icon,
        String resourceName,
        String wikiUrl,
        String summary,
        String kind,
        String wikiTitle,
        String wikiSection,
        List<QuestConditionSnapshot> conditions
) {
}
