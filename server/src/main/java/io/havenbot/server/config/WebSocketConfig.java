package io.havenbot.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.server.auth.OperatorSessionService;
import io.havenbot.server.service.*;
import org.springframework.context.annotation.Bean;
import io.havenbot.server.websocket.BotWebSocketHandler;
import io.havenbot.server.websocket.OperatorWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private static final int MAX_MESSAGE_BYTES = 8 * 1024 * 1024;

    private final OperatorSessionService operatorSessionService;
    private final BotSessionRegistry botSessionRegistry;
    private final BotService botService;
    private final BotFleetService botFleetService;
    private final LiveFeedService liveFeedService;
    private final AuditService auditService;
    private final BotActivityService botActivityService;
    private final ClipTriggerService clipTriggerService;
    private final ObjectMapper objectMapper;

    public WebSocketConfig(OperatorSessionService operatorSessionService, BotSessionRegistry botSessionRegistry,
                           BotService botService, BotFleetService botFleetService, LiveFeedService liveFeedService,
                           AuditService auditService, BotActivityService botActivityService,
                           ClipTriggerService clipTriggerService, ObjectMapper objectMapper) {
        this.operatorSessionService = operatorSessionService;
        this.botSessionRegistry = botSessionRegistry;
        this.botService = botService;
        this.botFleetService = botFleetService;
        this.liveFeedService = liveFeedService;
        this.auditService = auditService;
        this.botActivityService = botActivityService;
        this.clipTriggerService = clipTriggerService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES);
        container.setMaxBinaryMessageBufferSize(MAX_MESSAGE_BYTES);
        container.setMaxSessionIdleTimeout(120000L);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new OperatorWebSocketHandler(operatorSessionService, botSessionRegistry), "/ws/operator")
                .setAllowedOrigins("*");
        registry.addHandler(new BotWebSocketHandler(objectMapper, botSessionRegistry, botService, botFleetService, liveFeedService, auditService, botActivityService, clipTriggerService), "/ws/bot")
                .setAllowedOrigins("*");
    }
}
