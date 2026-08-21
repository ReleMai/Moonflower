package io.havenbot.server.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.protocol.BotEventEnvelope;
import io.havenbot.protocol.BotEventType;
import io.havenbot.protocol.BotStatus;
import io.havenbot.server.service.AuditService;
import io.havenbot.server.service.BotActivityService;
import io.havenbot.server.service.BotFleetService;
import io.havenbot.server.service.BotService;
import io.havenbot.server.service.BotSessionRegistry;
import io.havenbot.server.service.ClipTriggerService;
import io.havenbot.server.service.LiveFeedService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotWebSocketHandlerTest {
    @Test
    void fastStateUpdateBroadcastsWithoutPersistingState() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        BotSessionRegistry sessionRegistry = spy(new BotSessionRegistry(objectMapper));
        BotService botService = mock(BotService.class);
        BotFleetService botFleetService = mock(BotFleetService.class);
        LiveFeedService liveFeedService = mock(LiveFeedService.class);
        AuditService auditService = mock(AuditService.class);
        BotActivityService activityService = mock(BotActivityService.class);
        ClipTriggerService clipTriggerService = mock(ClipTriggerService.class);

        UUID botId = UUID.randomUUID();
        String token = sessionRegistry.issueLaunchToken(botId);
        WebSocketSession session = mockSession(botId, token);

        TestBotWebSocketHandler handler = new TestBotWebSocketHandler(
                objectMapper,
                sessionRegistry,
                botService,
                botFleetService,
                liveFeedService,
                auditService,
                activityService,
                clipTriggerService
        );
        handler.afterConnectionEstablished(session);

        handler.handle(session, envelopeJson(objectMapper, botId, BotEventType.FAST_STATE_UPDATE, """
                {
                  "botId": "%s",
                  "sessionStatus": "CONNECTED",
                  "automationPaused": false,
                  "capturedAt": "%s"
                }
                """.formatted(botId, Instant.now())));

        verify(botService, never()).updateState(any(), any(), any());
        verify(sessionRegistry).broadcastOperator(eq("fast-state-update"), any());
    }

    @Test
    void stateSnapshotPersistsDurableState() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        BotSessionRegistry sessionRegistry = spy(new BotSessionRegistry(objectMapper));
        BotService botService = mock(BotService.class);
        BotFleetService botFleetService = mock(BotFleetService.class);
        LiveFeedService liveFeedService = mock(LiveFeedService.class);
        AuditService auditService = mock(AuditService.class);
        BotActivityService activityService = mock(BotActivityService.class);
        ClipTriggerService clipTriggerService = mock(ClipTriggerService.class);

        UUID botId = UUID.randomUUID();
        String token = sessionRegistry.issueLaunchToken(botId);
        WebSocketSession session = mockSession(botId, token);
        when(botFleetService.liveStatusForSnapshot(botId, false)).thenReturn(BotStatus.RUNNING);

        TestBotWebSocketHandler handler = new TestBotWebSocketHandler(
                objectMapper,
                sessionRegistry,
                botService,
                botFleetService,
                liveFeedService,
                auditService,
                activityService,
                clipTriggerService
        );
        handler.afterConnectionEstablished(session);

        handler.handle(session, envelopeJson(objectMapper, botId, BotEventType.STATE_SNAPSHOT, """
                {
                  "botId": "%s",
                  "sessionStatus": "CONNECTED",
                  "automationPaused": false,
                  "capturedAt": "%s"
                }
                """.formatted(botId, Instant.now())));

        verify(botService).updateState(eq(botId), any(), eq(BotStatus.RUNNING));
        verify(sessionRegistry).broadcastOperator(eq("state-snapshot"), any());
    }

    private static WebSocketSession mockSession(UUID botId, String token) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        when(session.getUri()).thenReturn(new URI("ws://127.0.0.1/ws/bot?botId=" + botId + "&token=" + token));
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static String envelopeJson(ObjectMapper objectMapper, UUID botId, BotEventType type, String payloadJson) throws Exception {
        JsonNode payload = objectMapper.readTree(payloadJson);
        return objectMapper.writeValueAsString(new BotEventEnvelope(
                UUID.randomUUID().toString(),
                botId.toString(),
                type,
                payload,
                Instant.now()
        ));
    }

    private static final class TestBotWebSocketHandler extends BotWebSocketHandler {
        private TestBotWebSocketHandler(ObjectMapper objectMapper, BotSessionRegistry sessionRegistry, BotService botService,
                                        BotFleetService botFleetService, LiveFeedService liveFeedService, AuditService auditService,
                                        BotActivityService botActivityService, ClipTriggerService clipTriggerService) {
            super(objectMapper, sessionRegistry, botService, botFleetService, liveFeedService, auditService, botActivityService, clipTriggerService);
        }

        private void handle(WebSocketSession session, String payload) throws Exception {
            handleTextMessage(session, new TextMessage(payload));
        }
    }
}
