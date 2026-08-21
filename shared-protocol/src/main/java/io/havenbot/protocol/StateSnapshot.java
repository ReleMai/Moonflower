package io.havenbot.protocol;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record StateSnapshot(
        String botId,
        BotStatus status,
        String sessionStatus,
        String characterName,
        String worldName,
        PositionSnapshot position,
        HealthSnapshot health,
        MeterSnapshot stamina,
        MeterSnapshot energy,
        InventorySummary inventory,
        TaskSnapshot currentTask,
        Integer experience,
        Integer learningPoints,
        Map<String, Integer> attributes,
        List<StatDetailSnapshot> attributeDetails,
        Map<String, Integer> skills,
        List<StatDetailSnapshot> skillDetails,
        List<String> knownSkills,
        List<NamedIconSnapshot> knownSkillDetails,
        List<String> credos,
        List<NamedIconSnapshot> credoDetails,
        List<QuestSnapshot> currentQuests,
        Integer selectedQuestId,
        List<String> activeSkills,
        Map<String, Integer> visibleStats,
        List<NamedIconSnapshot> equipmentDetails,
        List<String> routeNames,
        Boolean automationPaused,
        Instant capturedAt
) {
}
