package io.havenbot.protocol;

public record QuestConditionSnapshot(
        String description,
        Integer done,
        String status
) {
}
