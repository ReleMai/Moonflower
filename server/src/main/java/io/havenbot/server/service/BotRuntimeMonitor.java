package io.havenbot.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.protocol.BotStatus;
import io.havenbot.server.model.BotRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BotRuntimeMonitor {
    private static final Duration STALE_SESSION_THRESHOLD = Duration.ofSeconds(30);
    private static final Duration LAUNCH_TIMEOUT = Duration.ofSeconds(90);

    private final BotService botService;
    private final BotSessionRegistry sessionRegistry;
    private final BotProcessSupervisor processSupervisor;
    private final BotFleetService botFleetService;
    private final TaskService taskService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public BotRuntimeMonitor(BotService botService, BotSessionRegistry sessionRegistry, BotProcessSupervisor processSupervisor,
                             BotFleetService botFleetService, TaskService taskService, AuditService auditService,
                             ObjectMapper objectMapper) {
        this.botService = botService;
        this.sessionRegistry = sessionRegistry;
        this.processSupervisor = processSupervisor;
        this.botFleetService = botFleetService;
        this.taskService = taskService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    public void reconcile() {
        for (BotRecord bot : botService.list()) {
            reconcileProcess(bot);
            reconcileConnectivity(botService.get(bot.id()).orElse(bot));
        }
    }

    private void reconcileProcess(BotRecord bot) {
        if (!processSupervisor.isTracked(bot.id()) || processSupervisor.isAlive(bot.id())) {
            return;
        }
        Integer exitCode = processSupervisor.exitCode(bot.id());
        BotProcessSupervisor.LaunchDetails launchDetails = processSupervisor.describe(bot.id()).orElse(null);
        String logTail = processSupervisor.readLogTail(bot.id(), 80, 12000);
        String summary = summarizeExit(logTail, exitCode);
        if (botFleetService.handleObservedProcessExit(bot.id(), exitCode, launchDetails, summary, logTail)) {
            processSupervisor.forget(bot.id());
            return;
        }
        processSupervisor.forget(bot.id());
        taskService.markInterruptedForBot(bot.id(), "Bot process exited unexpectedly.");
        botService.updateStatus(bot.id(), BotStatus.ERROR);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("exitCode", exitCode == null ? -1 : exitCode);
        if (launchDetails != null) {
            details.put("launchMode", launchDetails.launchMode());
            details.put("launchTarget", launchDetails.launchTarget());
            details.put("workingDirectory", launchDetails.workingDirectory());
            details.put("logPath", launchDetails.logPath());
        }
        if (!summary.isBlank()) {
            details.put("summary", summary);
        }
        if (!logTail.isBlank()) {
            details.put("logTail", logTail);
        }
        auditService.log(bot.id(), "server", "bot-process-exited", objectMapper.valueToTree(details));
        sessionRegistry.broadcastOperator("bot-status", Map.of("botId", bot.id(), "status", BotStatus.ERROR));
    }

    private void reconcileConnectivity(BotRecord bot) {
        if (sessionRegistry.isConnected(bot.id()) && sessionRegistry.isStale(bot.id(), STALE_SESSION_THRESHOLD) && bot.status() != BotStatus.ERROR) {
            taskService.markInterruptedForBot(bot.id(), "Bot heartbeat became stale.");
            botService.updateStatus(bot.id(), BotStatus.ERROR);
            auditService.log(bot.id(), "server", "bot-heartbeat-stale", objectMapper.valueToTree(Map.of(
                    "thresholdSeconds", STALE_SESSION_THRESHOLD.toSeconds()
            )));
            sessionRegistry.broadcastOperator("bot-status", Map.of("botId", bot.id(), "status", BotStatus.ERROR));
            return;
        }

        boolean timedOut = (bot.status() == BotStatus.LAUNCHING || bot.status() == BotStatus.CONNECTING)
                && !sessionRegistry.isConnected(bot.id())
                && bot.updatedAt().plus(LAUNCH_TIMEOUT).isBefore(Instant.now());
        if (timedOut) {
            taskService.markInterruptedForBot(bot.id(), "Bot launch timed out.");
            botService.updateStatus(bot.id(), BotStatus.ERROR);
            BotProcessSupervisor.LaunchDetails launchDetails = processSupervisor.describe(bot.id()).orElse(null);
            String logTail = processSupervisor.readLogTail(bot.id(), 80, 12000);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("timeoutSeconds", LAUNCH_TIMEOUT.toSeconds());
            if (launchDetails != null) {
                details.put("launchMode", launchDetails.launchMode());
                details.put("launchTarget", launchDetails.launchTarget());
                details.put("workingDirectory", launchDetails.workingDirectory());
                details.put("logPath", launchDetails.logPath());
            }
            String summary = summarizeExit(logTail, null);
            if (!summary.isBlank()) {
                details.put("summary", summary);
            }
            if (!logTail.isBlank()) {
                details.put("logTail", logTail);
            }
            auditService.log(bot.id(), "server", "bot-launch-timeout", objectMapper.valueToTree(details));
            sessionRegistry.broadcastOperator("bot-status", Map.of("botId", bot.id(), "status", BotStatus.ERROR));
        }
    }

    private String summarizeExit(String logTail, Integer exitCode) {
        if (logTail == null || logTail.isBlank()) {
            return exitCode == null ? "" : "Process exited with code " + exitCode + ".";
        }
        String[] lines = logTail.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isBlank() || looksLikePrompt(line)) {
                continue;
            }
            if (line.startsWith("Error:")
                    || line.startsWith("Exception")
                    || line.startsWith("Caused by:")
                    || line.toLowerCase().contains("unable to")) {
                return line;
            }
        }
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isBlank() && !looksLikePrompt(line)) {
                return line;
            }
        }
        return exitCode == null ? "" : "Process exited with code " + exitCode + ".";
    }

    private boolean looksLikePrompt(String line) {
        return line.matches("^[A-Za-z]:\\\\.*>.*$");
    }
}
