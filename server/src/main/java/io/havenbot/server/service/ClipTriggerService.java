package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClipTriggerService {
    private final ReplayClipService replayClipService;
    private final int replaySeconds;
    private final double damageDropThreshold;
    private final double lowHealthThreshold;
    private final double criticalHealthThreshold;
    private final Duration cooldown;
    private final Set<String> rareItemKeywords;
    private final Map<UUID, Double> previousHealth = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> previousInventory = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastTriggerAt = new ConcurrentHashMap<>();

    public ClipTriggerService(
            ReplayClipService replayClipService,
            @Value("${havenbot.replay.default-seconds:300}") int replaySeconds,
            @Value("${havenbot.replay.damage-drop-threshold:0.20}") double damageDropThreshold,
            @Value("${havenbot.replay.low-health-threshold:0.40}") double lowHealthThreshold,
            @Value("${havenbot.replay.critical-health-threshold:0.15}") double criticalHealthThreshold,
            @Value("${havenbot.replay.cooldown-seconds:60}") long cooldownSeconds,
            @Value("${havenbot.replay.rare-item-keywords:}") String rareItemKeywords
    ) {
        this.replayClipService = replayClipService;
        this.replaySeconds = replaySeconds;
        this.damageDropThreshold = damageDropThreshold;
        this.lowHealthThreshold = lowHealthThreshold;
        this.criticalHealthThreshold = criticalHealthThreshold;
        this.cooldown = Duration.ofSeconds(Math.max(1, cooldownSeconds));
        this.rareItemKeywords = parseKeywords(rareItemKeywords);
    }

    public void onBotRegistered(UUID botId) {
        try {
            replayClipService.ensureReplay(botId);
        } catch (RuntimeException ignored) {
        }
    }

    public void onStateSnapshot(UUID botId, JsonNode payload) {
        double currentHealth = payload.path("health").path("percentage").asDouble(Double.NaN);
        if (!Double.isNaN(currentHealth)) {
            Double previous = previousHealth.put(botId, currentHealth);
            if (previous != null) {
                double delta = previous - currentHealth;
                if (delta >= damageDropThreshold) {
                    trigger(botId, "auto-health-drop", "Health dropped sharply.", "health-drop");
                }
            }
            if (currentHealth <= criticalHealthThreshold) {
                trigger(botId, "auto-health-critical", "Health reached a critical threshold.", "health-critical");
            } else if (currentHealth <= lowHealthThreshold) {
                trigger(botId, "auto-health-low", "Health dropped below the warning threshold.", "health-low");
            }
        }

        if (!rareItemKeywords.isEmpty()) {
            Map<String, Integer> currentInventory = inventoryCounts(payload.path("inventory").path("itemDetails"));
            Map<String, Integer> previous = previousInventory.put(botId, currentInventory);
            if (previous != null) {
                for (Map.Entry<String, Integer> entry : currentInventory.entrySet()) {
                    int prior = previous.getOrDefault(entry.getKey(), 0);
                    if (entry.getValue() > prior && matchesRareKeyword(entry.getKey())) {
                        trigger(botId, "auto-rare-item", "Rare item detected: " + entry.getKey(), "rare-item-" + entry.getKey().toLowerCase(Locale.ROOT));
                        break;
                    }
                }
            }
        }
    }

    public void onClientError(UUID botId, String message) {
        trigger(botId, "auto-client-error", message == null || message.isBlank() ? "Client error detected." : message, "client-error");
    }

    public void onTransportError(UUID botId, String message) {
        trigger(botId, "auto-transport-error", message == null || message.isBlank() ? "Transport error detected." : message, "transport-error");
    }

    public void onDisconnected(UUID botId, String reason) {
        trigger(botId, "auto-disconnect", reason == null || reason.isBlank() ? "Bot disconnected." : reason, "disconnect");
        try {
            replayClipService.releaseReplay(botId);
        } catch (RuntimeException ignored) {
        }
        previousHealth.remove(botId);
        previousInventory.remove(botId);
        lastTriggerAt.keySet().removeIf(key -> key.startsWith(botId + ":"));
    }

    private void trigger(UUID botId, String triggerType, String reason, String cooldownKey) {
        if (!isOffCooldown(botId, cooldownKey)) {
            return;
        }
        try {
            replayClipService.saveTriggered(botId, triggerType, reason, replaySeconds);
        } catch (RuntimeException ignored) {
        }
    }

    private boolean isOffCooldown(UUID botId, String cooldownKey) {
        String key = botId + ":" + cooldownKey;
        Instant now = Instant.now();
        Instant last = lastTriggerAt.get(key);
        if (last != null && last.plus(cooldown).isAfter(now)) {
            return false;
        }
        lastTriggerAt.put(key, now);
        return true;
    }

    private Map<String, Integer> inventoryCounts(JsonNode itemDetails) {
        Map<String, Integer> counts = new HashMap<>();
        if (itemDetails == null || !itemDetails.isArray()) {
            return counts;
        }
        for (JsonNode item : itemDetails) {
            String name = item.path("name").asText("").trim();
            if (!name.isBlank()) {
                counts.merge(name, 1, Integer::sum);
            }
        }
        return counts;
    }

    private boolean matchesRareKeyword(String itemName) {
        String lower = itemName.toLowerCase(Locale.ROOT);
        for (String keyword : rareItemKeywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> parseKeywords(String value) {
        Set<String> parsed = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return parsed;
        }
        for (String part : value.split(",")) {
            String normalized = part.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                parsed.add(normalized);
            }
        }
        return parsed;
    }
}
