package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.server.model.MediaClipRecord;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class ReplayClipService {
    private final WebRtcGatewayService webRtcGatewayService;
    private final MediaClipService mediaClipService;
    private final AuditService auditService;
    private final BotActivityService botActivityService;
    private final BotSessionRegistry botSessionRegistry;
    private final ObjectMapper objectMapper;

    public ReplayClipService(WebRtcGatewayService webRtcGatewayService, MediaClipService mediaClipService,
                             AuditService auditService, BotActivityService botActivityService,
                             BotSessionRegistry botSessionRegistry, ObjectMapper objectMapper) {
        this.webRtcGatewayService = webRtcGatewayService;
        this.mediaClipService = mediaClipService;
        this.auditService = auditService;
        this.botActivityService = botActivityService;
        this.botSessionRegistry = botSessionRegistry;
        this.objectMapper = objectMapper;
    }

    public void ensureReplay(UUID botId) {
        webRtcGatewayService.ensureReplay(botId);
    }

    public void releaseReplay(UUID botId) {
        webRtcGatewayService.releaseReplay(botId);
    }

    public MediaClipRecord saveManual(UUID botId, Integer requestedSeconds, String reason) {
        return save(botId, "manual", reason == null || reason.isBlank() ? "Manual replay save." : reason, requestedSeconds, true);
    }

    public MediaClipRecord saveTriggered(UUID botId, String triggerType, String reason, Integer requestedSeconds) {
        return save(botId, triggerType, reason, requestedSeconds, false);
    }

    private MediaClipRecord save(UUID botId, String triggerType, String reason, Integer requestedSeconds, boolean operatorInitiated) {
        webRtcGatewayService.ensureReplay(botId);
        JsonNode response = webRtcGatewayService.saveReplay(botId, triggerType, reason, requestedSeconds);
        MediaClipRecord record = mediaClipService.register(botId, response);
        auditService.log(botId, operatorInitiated ? "operator" : "server", "replay-clip-saved", objectMapper.valueToTree(Map.of(
                "clipId", record.id().toString(),
                "fileName", record.fileName(),
                "triggerType", record.triggerType(),
                "reason", record.reason(),
                "durationSeconds", record.durationSeconds()
        )));
        var activity = botActivityService.log(botId, "server", "clip", "Saved replay clip.", objectMapper.valueToTree(Map.of(
                "clipId", record.id().toString(),
                "fileName", record.fileName(),
                "triggerType", record.triggerType(),
                "reason", record.reason(),
                "durationSeconds", record.durationSeconds()
        )));
        botSessionRegistry.broadcastOperator("activity-event", activity);
        botSessionRegistry.broadcastOperator("clip-saved", record);
        return record;
    }
}
