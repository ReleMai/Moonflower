package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.protocol.BotCommandEnvelope;
import io.havenbot.protocol.BotCommandType;
import io.havenbot.protocol.BotStatus;
import io.havenbot.protocol.TaskStatus;
import io.havenbot.server.model.BotActivityRecord;
import io.havenbot.server.model.BotRecord;
import io.havenbot.server.model.TaskRecord;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Service
public class BotFleetService {
    private static final Duration GRACEFUL_STOP_TIMEOUT = Duration.ofSeconds(12);

    private static final class PendingStop {
        private final Instant requestedAt;
        private volatile boolean disconnectObserved;
        private volatile boolean processExitObserved;
        private volatile boolean completionLogged;
        private ScheduledFuture<?> timeoutTask;

        private PendingStop(Instant requestedAt) {
            this.requestedAt = requestedAt;
        }
    }

    private final BotService botService;
    private final TaskService taskService;
    private final TaskPresetService taskPresetService;
    private final RoutePresetService routePresetService;
    private final BotSessionRegistry sessionRegistry;
    private final BotProcessSupervisor processSupervisor;
    private final AuditService auditService;
    private final BotActivityService botActivityService;
    private final LiveFeedService liveFeedService;
    private final ScreenshotService screenshotService;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<UUID, PendingStop> pendingStops = new ConcurrentHashMap<>();
    private final ScheduledExecutorService stopScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "bot-graceful-stop");
            thread.setDaemon(true);
            return thread;
        }
    });

    public BotFleetService(BotService botService, TaskService taskService, TaskPresetService taskPresetService,
                           RoutePresetService routePresetService, BotSessionRegistry sessionRegistry,
                           BotProcessSupervisor processSupervisor, AuditService auditService,
                           BotActivityService botActivityService, LiveFeedService liveFeedService,
                           ScreenshotService screenshotService, ObjectMapper objectMapper) {
        this.botService = botService;
        this.taskService = taskService;
        this.taskPresetService = taskPresetService;
        this.routePresetService = routePresetService;
        this.sessionRegistry = sessionRegistry;
        this.processSupervisor = processSupervisor;
        this.auditService = auditService;
        this.botActivityService = botActivityService;
        this.liveFeedService = liveFeedService;
        this.screenshotService = screenshotService;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    public void shutdown() {
        stopScheduler.shutdownNow();
    }

    public String launch(UUID botId) {
        BotRecord bot = botService.get(botId).orElseThrow(() -> new IllegalArgumentException("Bot not found."));
        clearPendingStop(botId);
        String registrationToken = sessionRegistry.issueLaunchToken(botId);
        botService.setRegistrationSecret(botId, registrationToken);
        botService.updateStatus(botId, BotStatus.LAUNCHING);
        try {
            BotProcessSupervisor.LaunchDetails launchDetails = processSupervisor.launch(bot, registrationToken);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("status", "launching");
            details.put("launchMode", launchDetails.launchMode());
            details.put("launchTarget", launchDetails.launchTarget());
            details.put("workingDirectory", launchDetails.workingDirectory());
            details.put("logPath", launchDetails.logPath());
            details.put("command", launchDetails.command());
            auditService.log(botId, "operator", "bot-launch", objectMapper.valueToTree(details));
            broadcastActivity(botId, "server", "session", "Launch requested.", objectMapper.valueToTree(details));
            return registrationToken;
        } catch (RuntimeException ex) {
            taskService.markInterruptedForBot(botId, "Bot launch failed: " + ex.getMessage());
            botService.updateStatus(botId, BotStatus.ERROR);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("message", ex.getMessage());
            if (ex.getCause() != null && ex.getCause().getMessage() != null && !ex.getCause().getMessage().isBlank()) {
                details.put("cause", ex.getCause().getMessage());
            }
            details.put("clientInstallPath", bot.clientInstallPath());
            if (bot.launchCommand() != null && !bot.launchCommand().isBlank()) {
                details.put("launchCommand", bot.launchCommand());
            }
            auditService.log(botId, "server", "bot-launch-failed", objectMapper.valueToTree(details));
            broadcastActivity(botId, "server", "error", "Launch failed.", objectMapper.valueToTree(details));
            sessionRegistry.broadcastOperator("bot-status", Map.of("botId", botId, "status", BotStatus.ERROR));
            throw ex;
        }
    }

    public void stop(UUID botId) {
        taskService.markInterruptedForBot(botId, "Bot stopped by operator.");
        if (!sessionRegistry.isConnected(botId)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("mode", "disconnected");
            details.put("status", "offline");
            forceStopNow(botId, "Bot stopped while disconnected.", "bot-stop-immediate", objectMapper.valueToTree(details));
            return;
        }

        PendingStop pendingStop = registerPendingStop(botId);
        botService.updateStatus(botId, BotStatus.STOPPING);
        sessionRegistry.broadcastOperator("bot-status", Map.of("botId", botId, "status", BotStatus.STOPPING));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", "stopping");
        details.put("timeoutSeconds", GRACEFUL_STOP_TIMEOUT.toSeconds());
        details.put("requestedAt", pendingStop.requestedAt.toString());
        auditService.log(botId, "operator", "bot-stop-graceful-requested", objectMapper.valueToTree(details));
        broadcastActivity(botId, "server", "session", "Graceful stop requested.", objectMapper.valueToTree(details));

        try {
            dispatchDirect(botId, BotCommandType.STOP_BOT, objectMapper.createObjectNode());
        } catch (RuntimeException ex) {
            clearPendingStop(botId);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("reason", "dispatch-failed");
            fallback.put("message", ex.getMessage());
            forceStopNow(botId, "Graceful stop fallback triggered after command dispatch failed.", "bot-stop-force-killed", objectMapper.valueToTree(fallback));
        }
    }

    public TaskRecord enqueueAction(UUID botId, String actionType, JsonNode params) {
        TaskRecord task = taskService.enqueue(botId, actionType, params);
        auditService.log(botId, "operator", "task-enqueued", objectMapper.valueToTree(Map.of("taskId", task.id(), "actionType", actionType)));
        broadcastActivity(botId, "server", "task", "Queued " + friendlyActionLabel(actionType) + ".", objectMapper.valueToTree(Map.of(
                "taskId", task.id().toString(),
                "actionType", actionType,
                "params", params
        )));
        dispatchNext(botId);
        return task;
    }

    public TaskRecord enqueueTaskPreset(UUID botId, UUID presetId) {
        var preset = taskPresetService.get(presetId).orElseThrow(() -> new IllegalArgumentException("Task preset not found."));
        return enqueueAction(botId, preset.actionType(), preset.params());
    }

    public TaskRecord enqueueRoutePreset(UUID botId, UUID routePresetId) {
        var preset = routePresetService.get(routePresetId).orElseThrow(() -> new IllegalArgumentException("Route preset not found."));
        return enqueueAction(botId, "route.start", preset.route());
    }

    public void beginTakeover(UUID botId) {
        botService.setTakeover(botId, true);
        botService.updateStatus(botId, BotStatus.TAKEOVER);
        dispatchDirect(botId, BotCommandType.BEGIN_TAKEOVER, objectMapper.createObjectNode());
        auditService.log(botId, "operator", "takeover-begin", objectMapper.valueToTree(Map.of()));
        broadcastActivity(botId, "server", "takeover", "Manual takeover started.", objectMapper.createObjectNode());
    }

    public void endTakeover(UUID botId) {
        botService.setTakeover(botId, false);
        botService.updateStatus(botId, BotStatus.IDLE);
        dispatchDirect(botId, BotCommandType.END_TAKEOVER, objectMapper.createObjectNode());
        auditService.log(botId, "operator", "takeover-end", objectMapper.valueToTree(Map.of()));
        broadcastActivity(botId, "server", "takeover", "Manual takeover ended.", objectMapper.createObjectNode());
        dispatchNext(botId);
    }

    public void pause(UUID botId) {
        taskService.markActiveInterruptedForBot(botId, "Bot paused by operator.");
        dispatchDirect(botId, BotCommandType.PAUSE_BOT, objectMapper.createObjectNode());
        botService.updateStatus(botId, BotStatus.IDLE);
        auditService.log(botId, "operator", "bot-paused", objectMapper.valueToTree(Map.of()));
        broadcastActivity(botId, "server", "session", "Bot paused to character select.", objectMapper.createObjectNode());
    }

    public void resume(UUID botId) {
        dispatchDirect(botId, BotCommandType.RESUME_BOT, objectMapper.createObjectNode());
        auditService.log(botId, "operator", "bot-resumed", objectMapper.valueToTree(Map.of()));
        broadcastActivity(botId, "server", "session", "Bot resume requested.", objectMapper.createObjectNode());
    }

    public void requestScreenshot(UUID botId) {
        dispatchDirect(botId, BotCommandType.REQUEST_SCREENSHOT, objectMapper.createObjectNode());
    }

    public void focusClient(UUID botId) {
        dispatchDirect(botId, BotCommandType.FOCUS_CLIENT, objectMapper.createObjectNode());
        broadcastActivity(botId, "server", "control", "Focused client window.", objectMapper.createObjectNode());
    }

    public void startLiveFeed(UUID botId, int intervalMillis) {
        int interval = Math.max(intervalMillis, 50);
        dispatchDirect(botId, BotCommandType.START_LIVE_FEED, objectMapper.valueToTree(Map.of(
                "intervalMillis", interval
        )));
        auditService.log(botId, "operator", "live-feed-start", objectMapper.valueToTree(Map.of("intervalMillis", interval)));
        broadcastActivity(botId, "server", "control", "Live feed started.", objectMapper.valueToTree(Map.of("intervalMillis", interval)));
    }

    public void stopLiveFeed(UUID botId) {
        dispatchDirect(botId, BotCommandType.STOP_LIVE_FEED, objectMapper.createObjectNode());
        auditService.log(botId, "operator", "live-feed-stop", objectMapper.valueToTree(Map.of()));
        broadcastActivity(botId, "server", "control", "Live feed stopped.", objectMapper.createObjectNode());
    }

    public void startScreenshotStream(UUID botId, int intervalSeconds) {
        startLiveFeed(botId, Math.max(intervalSeconds, 1) * 1000);
    }

    public void stopScreenshotStream(UUID botId) {
        stopLiveFeed(botId);
    }

    public void saveCurrentFrame(UUID botId) {
        var record = liveFeedService.saveCurrentFrame(botId);
        auditService.log(botId, "operator", "live-frame-saved", objectMapper.valueToTree(Map.of(
                "screenshotId", record.id().toString(),
                "fileName", record.fileName()
        )));
        broadcastActivity(botId, "server", "control", "Saved current live frame.", objectMapper.valueToTree(Map.of(
                "screenshotId", record.id().toString(),
                "fileName", record.fileName()
        )));
    }

    public void sendRemoteInput(UUID botId, JsonNode payload) {
        dispatchDirect(botId, BotCommandType.REMOTE_INPUT, payload);
    }

    public void abort(UUID botId) {
        taskService.markInterruptedForBot(botId, "Aborted by operator.");
        dispatchDirect(botId, BotCommandType.ABORT_TASK, objectMapper.createObjectNode());
        auditService.log(botId, "operator", "task-abort", objectMapper.valueToTree(Map.of()));
        broadcastActivity(botId, "server", "task", "Aborted active task queue.", objectMapper.createObjectNode());
        dispatchNext(botId);
    }

    public void interruptAll(UUID botId, String message) {
        taskService.markInterruptedForBot(botId, message);
    }

    public int clearQueued(UUID botId) {
        int cleared = taskService.cancelQueuedForBot(botId, "Cleared by operator.");
        auditService.log(botId, "operator", "queue-cleared", objectMapper.valueToTree(Map.of("count", cleared)));
        broadcastActivity(botId, "server", "task", "Cleared " + cleared + " queued task(s).", objectMapper.valueToTree(Map.of("count", cleared)));
        return cleared;
    }

    public void cancelTask(UUID taskId) {
        TaskRecord task = taskService.get(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found."));
        taskService.markCanceled(taskId, "Canceled by operator.");
        auditService.log(task.botId(), "operator", "task-canceled", objectMapper.valueToTree(Map.of("taskId", taskId)));
        if (sessionRegistry.isConnected(task.botId())) {
            dispatchDirect(task.botId(), BotCommandType.ABORT_TASK, objectMapper.valueToTree(Map.of("taskId", taskId.toString())));
        }
        dispatchNext(task.botId());
    }

    public void markTaskStarted(UUID taskId) {
        TaskRecord task = taskService.get(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found."));
        taskService.markStarted(taskId);
        broadcastActivity(task.botId(), "server", "task", "Started " + friendlyActionLabel(task.actionType()) + ".", objectMapper.valueToTree(Map.of(
                "taskId", taskId.toString(),
                "actionType", task.actionType()
        )));
    }

    public void markTaskCompleted(UUID botId, UUID taskId) {
        TaskRecord task = taskService.get(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found."));
        taskService.markCompleted(taskId);
        broadcastActivity(botId, "server", "task", "Completed " + friendlyActionLabel(task.actionType()) + ".", objectMapper.valueToTree(Map.of(
                "taskId", taskId.toString(),
                "actionType", task.actionType()
        )));
        dispatchNext(botId);
    }

    public void markTaskFailed(UUID botId, UUID taskId, String message) {
        TaskRecord task = taskService.get(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found."));
        taskService.markFailed(taskId, message);
        broadcastActivity(botId, "server", "task", "Failed " + friendlyActionLabel(task.actionType()) + ".", objectMapper.valueToTree(Map.of(
                "taskId", taskId.toString(),
                "actionType", task.actionType(),
                "message", message
        )));
        dispatchNext(botId);
    }

    public BotStatus liveStatusForSnapshot(UUID botId, boolean automationPaused) {
        if (isGracefulStopPending(botId)) {
            return BotStatus.STOPPING;
        }
        return automationPaused ? BotStatus.IDLE : BotStatus.RUNNING;
    }

    public boolean handleObservedProcessExit(UUID botId, Integer exitCode, BotProcessSupervisor.LaunchDetails launchDetails,
                                             String summary, String logTail) {
        PendingStop pendingStop = pendingStops.get(botId);
        if (pendingStop == null) {
            return false;
        }
        pendingStop.processExitObserved = true;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("exitCode", exitCode == null ? -1 : exitCode);
        details.put("disconnectObserved", pendingStop.disconnectObserved);
        details.put("timeoutSeconds", GRACEFUL_STOP_TIMEOUT.toSeconds());
        if (launchDetails != null) {
            details.put("launchMode", launchDetails.launchMode());
            details.put("launchTarget", launchDetails.launchTarget());
            details.put("workingDirectory", launchDetails.workingDirectory());
            details.put("logPath", launchDetails.logPath());
        }
        if (summary != null && !summary.isBlank()) {
            details.put("summary", summary);
        }
        if (logTail != null && !logTail.isBlank()) {
            details.put("logTail", logTail);
        }
        logGracefulStopCompletion(botId, pendingStop, objectMapper.valueToTree(details), "Bot logged out successfully.");
        if (pendingStop.disconnectObserved || !sessionRegistry.isConnected(botId)) {
            markOffline(botId);
            clearPendingStop(botId);
        }
        return true;
    }

    public void handleTransportError(UUID botId, Throwable exception) {
        if (isGracefulStopPending(botId)) {
            return;
        }
        botService.updateStatus(botId, BotStatus.ERROR);
        interruptAll(botId, "Bot connection transport error.");
        auditService.log(botId, "server", "transport-error", objectMapper.valueToTree(Map.of("message", exception.getMessage())));
        broadcastActivity(botId, "server", "error", "Transport error interrupted the bot connection.", objectMapper.valueToTree(Map.of(
                "message", exception.getMessage() == null ? "Unknown transport error" : exception.getMessage()
        )));
        sessionRegistry.broadcastOperator("bot-status", Map.of("botId", botId, "status", BotStatus.ERROR));
    }

    public void handleConnectionClosed(UUID botId, String reason) {
        cleanupUnsavedScreenshots(botId);
        PendingStop pendingStop = pendingStops.get(botId);
        if (pendingStop != null) {
            pendingStop.disconnectObserved = true;
            logGracefulStopCompletion(botId, pendingStop, objectMapper.valueToTree(Map.of(
                    "reason", reason == null ? "" : reason,
                    "timeoutSeconds", GRACEFUL_STOP_TIMEOUT.toSeconds(),
                    "processExited", pendingStop.processExitObserved
            )), "Bot logged out successfully.");
            markOffline(botId);
            if (pendingStop.processExitObserved || !processSupervisor.isTracked(botId) || !processSupervisor.isAlive(botId)) {
                clearPendingStop(botId);
            }
            return;
        }

        botService.updateStatus(botId, BotStatus.OFFLINE);
        interruptAll(botId, "Bot disconnected.");
        auditService.log(botId, "server", "bot-disconnected", objectMapper.valueToTree(Map.of("status", reason == null ? "" : reason)));
        broadcastActivity(botId, "server", "session", "Bot disconnected.", objectMapper.valueToTree(Map.of("reason", reason == null ? "" : reason)));
        sessionRegistry.broadcastOperator("bot-status", Map.of("botId", botId, "status", BotStatus.OFFLINE));
    }

    public boolean isGracefulStopPending(UUID botId) {
        return pendingStops.containsKey(botId);
    }

    public void dispatchNext(UUID botId) {
        botService.get(botId).ifPresent(bot -> {
            if (bot.takeoverActive()) {
                return;
            }
            if (!sessionRegistry.isConnected(botId)) {
                return;
            }
            if (!isReadyForActionDispatch(bot)) {
                return;
            }
            boolean runningTaskExists = taskService.listForBot(botId).stream()
                    .anyMatch(task -> task.status() == TaskStatus.DISPATCHED || task.status() == TaskStatus.RUNNING);
            if (runningTaskExists) {
                return;
            }
            taskService.nextQueued(botId).ifPresent(task -> {
                taskService.markDispatched(task.id());
                dispatchDirect(botId, BotCommandType.RUN_ACTION, objectMapper.valueToTree(Map.of(
                        "taskId", task.id().toString(),
                        "actionType", task.actionType(),
                        "params", task.params()
                )));
            });
        });
    }

    private void dispatchDirect(UUID botId, BotCommandType type, JsonNode payload) {
        BotCommandEnvelope envelope = new BotCommandEnvelope(
                UUID.randomUUID().toString(),
                botId.toString(),
                type,
                payload,
                Instant.now()
        );
        sessionRegistry.sendToBot(botId, envelope);
        sessionRegistry.broadcastOperator("command-dispatched", envelope);
    }

    private PendingStop registerPendingStop(UUID botId) {
        clearPendingStop(botId);
        PendingStop pendingStop = new PendingStop(Instant.now());
        pendingStop.timeoutTask = stopScheduler.schedule(
                () -> forceStopAfterTimeout(botId),
                GRACEFUL_STOP_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
        );
        pendingStops.put(botId, pendingStop);
        return pendingStop;
    }

    private void clearPendingStop(UUID botId) {
        PendingStop removed = pendingStops.remove(botId);
        if (removed != null && removed.timeoutTask != null) {
            removed.timeoutTask.cancel(false);
        }
    }

    private void forceStopAfterTimeout(UUID botId) {
        PendingStop pendingStop = pendingStops.get(botId);
        if (pendingStop == null) {
            return;
        }
        if (!processSupervisor.isTracked(botId) || !processSupervisor.isAlive(botId)) {
            if (!sessionRegistry.isConnected(botId)) {
                markOffline(botId);
            }
            if (pendingStop.disconnectObserved || pendingStop.processExitObserved || !sessionRegistry.isConnected(botId)) {
                clearPendingStop(botId);
            }
            return;
        }

        pendingStop.processExitObserved = true;
        processSupervisor.stop(botId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("timeoutSeconds", GRACEFUL_STOP_TIMEOUT.toSeconds());
        details.put("disconnectObserved", pendingStop.disconnectObserved);
        details.put("requestedAt", pendingStop.requestedAt.toString());
        auditService.log(botId, "server", "bot-stop-force-killed", objectMapper.valueToTree(details));
        broadcastActivity(botId, "server", "session", "Graceful stop timed out and the client process was force-killed.", objectMapper.valueToTree(details));
        if (!sessionRegistry.isConnected(botId)) {
            markOffline(botId);
            clearPendingStop(botId);
        }
    }

    private void forceStopNow(UUID botId, String message, String auditEventType, JsonNode details) {
        clearPendingStop(botId);
        processSupervisor.stop(botId);
        markOffline(botId);
        auditService.log(botId, "operator", auditEventType, details);
        broadcastActivity(botId, "server", "session", message, details);
    }

    private void markOffline(UUID botId) {
        cleanupUnsavedScreenshots(botId);
        botService.updateStatus(botId, BotStatus.OFFLINE);
        sessionRegistry.broadcastOperator("bot-status", Map.of("botId", botId, "status", BotStatus.OFFLINE));
    }

    private void cleanupUnsavedScreenshots(UUID botId) {
        int purged = screenshotService.purgeUnsaved(botId);
        if (purged > 0) {
            auditService.log(botId, "server", "unsaved-screenshots-purged", objectMapper.valueToTree(Map.of("count", purged)));
        }
    }

    private void logGracefulStopCompletion(UUID botId, PendingStop pendingStop, JsonNode details, String message) {
        if (pendingStop.completionLogged) {
            return;
        }
        pendingStop.completionLogged = true;
        auditService.log(botId, "server", "bot-stop-graceful-complete", details);
        broadcastActivity(botId, "server", "session", message, details);
    }

    private BotActivityRecord broadcastActivity(UUID botId, String source, String category, String message, JsonNode details) {
        BotActivityRecord record = botActivityService.log(botId, source, category, message, details);
        sessionRegistry.broadcastOperator("activity-event", record);
        return record;
    }

    private boolean isReadyForActionDispatch(BotRecord bot) {
        JsonNode state = bot.lastState();
        return state != null && "GAME".equals(state.path("screen").asText(""));
    }

    private String friendlyActionLabel(String actionType) {
        return switch (actionType) {
            case "cleanup.start" -> "Cleanup";
            case "cleanup.stop" -> "Stop Cleanup";
            case "fishing.start" -> "Fishing";
            case "fishing.stop" -> "Stop Fishing";
            case "route.start" -> "Route";
            case "route.stop" -> "Stop Route";
            case "inventory.sort" -> "Inventory Sort";
            case "auto-repeat-flower" -> "Flower Auto-Repeat";
            case "auto-repeat-flower.clear" -> "Clear Flower Auto-Repeat";
            case "grubgrub.start" -> "Grub-Grub";
            case "grubgrub.stop" -> "Stop Grub-Grub";
            case "tar-kiln.start" -> "Tar Kiln";
            case "tar-kiln.stop" -> "Stop Tar Kiln";
            case "roasting.start" -> "Roasting";
            case "roasting.stop" -> "Stop Roasting";
            case "cellar.start" -> "Cellar Digging";
            case "cellar.stop" -> "Stop Cellar Digging";
            case "ocean-scout.start" -> "Ocean Scout";
            case "ocean-scout.stop" -> "Stop Ocean Scout";
            case "safe-logout" -> "Safe Logout";
            default -> actionType;
        };
    }
}
