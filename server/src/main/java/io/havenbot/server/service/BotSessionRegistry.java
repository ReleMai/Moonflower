package io.havenbot.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.protocol.BotCommandEnvelope;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BotSessionRegistry {
    private record PendingLaunchToken(UUID botId, Instant expiresAt) {
    }

    private final ObjectMapper objectMapper;
    private final Map<UUID, WebSocketSession> botSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> botLastSeen = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> operatorSessions = ConcurrentHashMap.newKeySet();
    private final Map<String, PendingLaunchToken> pendingLaunchTokens = new ConcurrentHashMap<>();

    public BotSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void registerOperator(WebSocketSession session) {
        operatorSessions.add(session);
    }

    public void removeOperator(WebSocketSession session) {
        operatorSessions.remove(session);
    }

    public String issueLaunchToken(UUID botId) {
        String token = UUID.randomUUID().toString();
        pendingLaunchTokens.put(token, new PendingLaunchToken(botId, Instant.now().plusSeconds(300)));
        return token;
    }

    public boolean acceptLaunchToken(UUID botId, String token) {
        PendingLaunchToken pending = pendingLaunchTokens.remove(token);
        return pending != null && pending.botId().equals(botId) && pending.expiresAt().isAfter(Instant.now());
    }

    public void registerBot(UUID botId, WebSocketSession session) {
        botSessions.put(botId, session);
        markBotSeen(botId);
    }

    public void removeBot(UUID botId) {
        botSessions.remove(botId);
        botLastSeen.remove(botId);
    }

    public boolean isConnected(UUID botId) {
        WebSocketSession session = botSessions.get(botId);
        return session != null && session.isOpen();
    }

    public void markBotSeen(UUID botId) {
        if (botId == null) {
            return;
        }
        botLastSeen.put(botId, Instant.now());
    }

    public Optional<Instant> getLastSeen(UUID botId) {
        return Optional.ofNullable(botLastSeen.get(botId));
    }

    public boolean isStale(UUID botId, Duration threshold) {
        Instant seenAt = botLastSeen.get(botId);
        return (seenAt != null) && seenAt.plus(threshold).isBefore(Instant.now());
    }

    public void sendToBot(UUID botId, BotCommandEnvelope envelope) {
        WebSocketSession session = botSessions.get(botId);
        if (session == null || !session.isOpen()) {
            throw new IllegalStateException("Bot is not connected.");
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to send WebSocket message to bot.", ex);
        }
    }

    public void broadcastOperator(String type, Object payload) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("type", type, "payload", payload));
            for (WebSocketSession session : operatorSessions) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(body));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to broadcast operator message.", ex);
        }
    }
}
