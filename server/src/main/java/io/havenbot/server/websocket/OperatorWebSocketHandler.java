package io.havenbot.server.websocket;

import io.havenbot.server.auth.OperatorSessionService;
import io.havenbot.server.service.BotSessionRegistry;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class OperatorWebSocketHandler extends TextWebSocketHandler {
    private final OperatorSessionService operatorSessionService;
    private final BotSessionRegistry sessionRegistry;

    public OperatorWebSocketHandler(OperatorSessionService operatorSessionService, BotSessionRegistry sessionRegistry) {
        this.operatorSessionService = operatorSessionService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = getQueryValue(session, "token");
        if (!operatorSessionService.isValid(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid operator token."));
            return;
        }
        sessionRegistry.registerOperator(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.removeOperator(session);
    }

    private String getQueryValue(WebSocketSession session, String name) {
        String query = session.getUri() == null ? null : session.getUri().getQuery();
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            String[] bits = part.split("=", 2);
            if (bits.length == 2 && bits[0].equals(name)) {
                return bits[1];
            }
        }
        return null;
    }
}

