package io.havenbot.server.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.protocol.BotEventEnvelope;
import io.havenbot.protocol.BotStatus;
import io.havenbot.server.model.BotActivityRecord;
import io.havenbot.server.service.*;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BotWebSocketHandler extends TextWebSocketHandler {
    private static final int MAX_MESSAGE_BYTES = 8 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final BotSessionRegistry sessionRegistry;
    private final BotService botService;
    private final BotFleetService botFleetService;
    private final LiveFeedService liveFeedService;
    private final AuditService auditService;
    private final BotActivityService botActivityService;
    private final ClipTriggerService clipTriggerService;
    private final Map<String, UUID> sessionToBot = new ConcurrentHashMap<>();

    public BotWebSocketHandler(ObjectMapper objectMapper, BotSessionRegistry sessionRegistry, BotService botService,
                               BotFleetService botFleetService, LiveFeedService liveFeedService, AuditService auditService,
                               BotActivityService botActivityService, ClipTriggerService clipTriggerService) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.botService = botService;
        this.botFleetService = botFleetService;
        this.liveFeedService = liveFeedService;
        this.auditService = auditService;
        this.botActivityService = botActivityService;
        this.clipTriggerService = clipTriggerService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.setTextMessageSizeLimit(MAX_MESSAGE_BYTES);
        session.setBinaryMessageSizeLimit(MAX_MESSAGE_BYTES);
        UUID botId = UUID.fromString(requireQueryValue(session, "botId"));
        String token = requireQueryValue(session, "token");
        if (!sessionRegistry.acceptLaunchToken(botId, token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid bot registration token."));
            return;
        }
        sessionRegistry.registerBot(botId, session);
        sessionToBot.put(session.getId(), botId);
        botService.updateStatus(botId, BotStatus.CONNECTING);
        sessionRegistry.broadcastOperator("bot-status", Map.of("botId", botId, "status", BotStatus.CONNECTING));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID botId = sessionToBot.get(session.getId());
        if (botId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Bot session not registered."));
            return;
        }
        sessionRegistry.markBotSeen(botId);
        BotEventEnvelope envelope = objectMapper.readValue(message.getPayload(), BotEventEnvelope.class);
        JsonNode payload = envelope.payload();
        switch (envelope.type()) {
            case BOT_REGISTERED -> {
                botService.updateStatus(botId, BotStatus.IDLE);
                botService.updateState(botId, payload.path("state"), BotStatus.IDLE);
                sessionRegistry.broadcastOperator("bot-registered", envelope);
                clipTriggerService.onBotRegistered(botId);
                botFleetService.dispatchNext(botId);
            }
            case HEARTBEAT -> sessionRegistry.broadcastOperator("heartbeat", envelope);
            case FAST_STATE_UPDATE -> sessionRegistry.broadcastOperator("fast-state-update", envelope);
            case STATE_SNAPSHOT -> {
                botService.updateState(botId, payload, botFleetService.liveStatusForSnapshot(botId, payload.path("automationPaused").asBoolean(false)));
                sessionRegistry.broadcastOperator("state-snapshot", envelope);
                clipTriggerService.onStateSnapshot(botId, payload);
                if ("GAME".equals(payload.path("screen").asText("")) && !payload.path("automationPaused").asBoolean(false)) {
                    botFleetService.dispatchNext(botId);
                }
            }
            case TASK_STARTED -> {
                botFleetService.markTaskStarted(UUID.fromString(payload.path("taskId").asText()));
                sessionRegistry.broadcastOperator("task-started", envelope);
            }
            case TASK_COMPLETED -> {
                botFleetService.markTaskCompleted(botId, UUID.fromString(payload.path("taskId").asText()));
                sessionRegistry.broadcastOperator("task-completed", envelope);
            }
            case TASK_FAILED -> {
                botFleetService.markTaskFailed(botId, UUID.fromString(payload.path("taskId").asText()), payload.path("message").asText("Task failed"));
                sessionRegistry.broadcastOperator("task-failed", envelope);
            }
            case TAKEOVER_CHANGED -> {
                boolean active = payload.path("active").asBoolean(false);
                botService.setTakeover(botId, active);
                botService.updateStatus(botId, active ? BotStatus.TAKEOVER : BotStatus.IDLE);
                sessionRegistry.broadcastOperator("takeover-changed", envelope);
            }
            case LIVE_FRAME_READY, SCREENSHOT_READY -> {
                var frame = liveFeedService.publishFrame(botId, payload.path("metadata"), payload.path("base64Content").asText());
                sessionRegistry.broadcastOperator("live-frame-ready", Map.of(
                        "botId", botId,
                        "createdAt", frame.createdAt().toString(),
                        "width", frame.width(),
                        "height", frame.height(),
                        "mediaType", frame.mediaType()
                ));
            }
            case ACTIVITY_EVENT -> {
                JsonNode details = payload.has("details") ? payload.get("details") : payload;
                BotActivityRecord record = botActivityService.log(
                        botId,
                        payload.path("source").asText("client"),
                        payload.path("category").asText("general"),
                        payload.path("message").asText("Activity event"),
                        details
                );
                sessionRegistry.broadcastOperator("activity-event", record);
            }
            case CLIENT_ERROR -> {
                auditService.log(botId, "client", "client-error", payload);
                botService.updateStatus(botId, BotStatus.ERROR);
                clipTriggerService.onClientError(botId, payload.path("message").asText("Client error"));
                sessionRegistry.broadcastOperator("client-error", envelope);
            }
            case SESSION_CHANGED -> {
                sessionRegistry.broadcastOperator("session-changed", envelope);
                if ("GAME".equals(payload.path("screen").asText("")) && !payload.path("automationPaused").asBoolean(false)) {
                    botFleetService.dispatchNext(botId);
                }
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        UUID botId = sessionToBot.get(session.getId());
        if (botId != null) {
            clipTriggerService.onTransportError(botId, exception.getMessage());
            botFleetService.handleTransportError(botId, exception);
        }
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UUID botId = sessionToBot.remove(session.getId());
        if (botId != null) {
            sessionRegistry.removeBot(botId);
            clipTriggerService.onDisconnected(botId, status.getReason());
            liveFeedService.clearBot(botId);
            botFleetService.handleConnectionClosed(botId, status.getReason());
        }
    }

    private String requireQueryValue(WebSocketSession session, String name) throws IOException {
        String query = session.getUri() == null ? null : session.getUri().getQuery();
        if (query == null) {
            throw new IOException("Missing query.");
        }
        for (String part : query.split("&")) {
            String[] bits = part.split("=", 2);
            if (bits.length == 2 && bits[0].equals(name)) {
                return bits[1];
            }
        }
        throw new IOException("Missing query parameter: " + name);
    }
}
