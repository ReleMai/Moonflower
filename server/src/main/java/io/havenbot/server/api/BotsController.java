package io.havenbot.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.havenbot.server.model.BotRecord;
import io.havenbot.server.model.BotActivityRecord;
import io.havenbot.server.model.MediaClipRecord;
import io.havenbot.server.model.TaskRecord;
import io.havenbot.server.service.BotActivityService;
import io.havenbot.server.service.BotFleetService;
import io.havenbot.server.service.BotService;
import io.havenbot.server.service.LiveFeedService;
import io.havenbot.server.service.ReplayClipService;
import io.havenbot.server.service.WebRtcGatewayService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bots")
public class BotsController {
    private final BotService botService;
    private final BotFleetService botFleetService;
    private final BotActivityService botActivityService;
    private final LiveFeedService liveFeedService;
    private final WebRtcGatewayService webRtcGatewayService;
    private final ReplayClipService replayClipService;

    public BotsController(BotService botService, BotFleetService botFleetService, BotActivityService botActivityService,
                          LiveFeedService liveFeedService, WebRtcGatewayService webRtcGatewayService,
                          ReplayClipService replayClipService) {
        this.botService = botService;
        this.botFleetService = botFleetService;
        this.botActivityService = botActivityService;
        this.liveFeedService = liveFeedService;
        this.webRtcGatewayService = webRtcGatewayService;
        this.replayClipService = replayClipService;
    }

    @GetMapping
    public List<BotView> list() {
        return botService.list().stream().map(BotView::from).toList();
    }

    @GetMapping("/{id}")
    public BotView get(@PathVariable UUID id) {
        return BotView.from(botService.get(id).orElseThrow(() -> new IllegalArgumentException("Bot not found.")));
    }

    @PostMapping
    public BotView create(@RequestBody UpsertBotRequest request) {
        return BotView.from(botService.create(request.name(), request.accountId(), request.clientInstallPath(), request.preferredCharacter(),
                request.preferredWorld(), request.profileName(), request.launchCommand()));
    }

    @PutMapping("/{id}")
    public BotView update(@PathVariable UUID id, @RequestBody UpsertBotRequest request) {
        return BotView.from(botService.update(id, request.name(), request.accountId(), request.clientInstallPath(), request.preferredCharacter(),
                request.preferredWorld(), request.profileName(), request.launchCommand()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        botService.delete(id);
    }

    @PostMapping("/{id}/launch")
    public Map<String, String> launch(@PathVariable UUID id) {
        return Map.of("registrationToken", botFleetService.launch(id));
    }

    @PostMapping("/{id}/stop")
    public void stop(@PathVariable UUID id) {
        botFleetService.stop(id);
    }

    @PostMapping("/{id}/pause")
    public void pause(@PathVariable UUID id) {
        botFleetService.pause(id);
    }

    @PostMapping("/{id}/resume")
    public void resume(@PathVariable UUID id) {
        botFleetService.resume(id);
    }

    @PostMapping("/{id}/abort")
    public void abort(@PathVariable UUID id) {
        botFleetService.abort(id);
    }

    @PostMapping("/{id}/queue/clear")
    public Map<String, Integer> clearQueue(@PathVariable UUID id) {
        return Map.of("cleared", botFleetService.clearQueued(id));
    }

    @PostMapping("/{id}/takeover/begin")
    public void beginTakeover(@PathVariable UUID id) {
        botFleetService.beginTakeover(id);
    }

    @PostMapping("/{id}/takeover/end")
    public void endTakeover(@PathVariable UUID id) {
        botFleetService.endTakeover(id);
    }

    @PostMapping("/{id}/screenshot")
    public void requestScreenshot(@PathVariable UUID id) {
        botFleetService.requestScreenshot(id);
    }

    @PostMapping("/{id}/live-feed/start")
    public void startLiveFeed(@PathVariable UUID id, @RequestBody(required = false) ScreenshotStreamRequest request) {
        int interval = request == null ? 50 : request.resolveIntervalMillis(50);
        botFleetService.startLiveFeed(id, interval);
    }

    @PostMapping("/{id}/live-feed/stop")
    public void stopLiveFeed(@PathVariable UUID id) {
        botFleetService.stopLiveFeed(id);
    }

    @PostMapping("/{id}/live-frame/save")
    public void saveLiveFrame(@PathVariable UUID id) {
        botFleetService.saveCurrentFrame(id);
    }

    @GetMapping(value = "/{id}/live-feed", produces = "multipart/x-mixed-replace; boundary=frame")
    public ResponseEntity<StreamingResponseBody> liveFeed(@PathVariable UUID id) {
        StreamingResponseBody stream = outputStream -> liveFeedService.stream(id, outputStream);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .contentType(MediaType.parseMediaType("multipart/x-mixed-replace; boundary=frame"))
                .body(stream);
    }

    @GetMapping("/{id}/activity")
    public List<BotActivityRecord> activity(@PathVariable UUID id, @RequestParam(required = false) Integer limit) {
        return botActivityService.listForBot(id, limit);
    }

    @PostMapping("/{id}/webrtc/offer")
    public JsonNode webrtcOffer(@PathVariable UUID id,
                                @RequestBody WebRtcOfferRequest request,
                                @RequestHeader(value = "X-Operator-Token", required = false) String headerToken,
                                @RequestParam(value = "token", required = false) String queryToken) {
        String operatorToken = headerToken != null && !headerToken.isBlank() ? headerToken : queryToken;
        if (operatorToken == null || operatorToken.isBlank()) {
            throw new IllegalArgumentException("Operator token is required.");
        }
        return webRtcGatewayService.createAnswer(id, operatorToken, request.sdp(), request.type());
    }

    @GetMapping("/webrtc/health")
    public JsonNode webrtcHealth() {
        return webRtcGatewayService.health();
    }

    @PostMapping("/{id}/focus")
    public void focusClient(@PathVariable UUID id) {
        botFleetService.focusClient(id);
    }

    @PostMapping("/{id}/replay/save")
    public MediaClipRecord saveReplay(@PathVariable UUID id, @RequestBody(required = false) ReplaySaveRequest request) {
        Integer requestedSeconds = request == null ? null : request.requestedSeconds();
        String reason = request == null ? null : request.reason();
        return replayClipService.saveManual(id, requestedSeconds, reason);
    }

    @PostMapping("/{id}/screenshot-stream/start")
    public void startScreenshotStream(@PathVariable UUID id, @RequestBody(required = false) ScreenshotStreamRequest request) {
        int interval = request == null || request.intervalSeconds() == null ? 3 : request.intervalSeconds();
        botFleetService.startScreenshotStream(id, interval);
    }

    @PostMapping("/{id}/screenshot-stream/stop")
    public void stopScreenshotStream(@PathVariable UUID id) {
        botFleetService.stopScreenshotStream(id);
    }

    @PostMapping("/{id}/remote-input")
    public void remoteInput(@PathVariable UUID id, @RequestBody JsonNode payload) {
        botFleetService.sendRemoteInput(id, payload);
    }

    @PostMapping("/{id}/actions")
    public TaskRecord runAction(@PathVariable UUID id, @RequestBody RunActionRequest request) {
        return botFleetService.enqueueAction(id, request.actionType(), request.params());
    }

    @PostMapping("/{id}/task-presets/{presetId}")
    public TaskRecord runTaskPreset(@PathVariable UUID id, @PathVariable UUID presetId) {
        return botFleetService.enqueueTaskPreset(id, presetId);
    }

    @PostMapping("/{id}/route-presets/{presetId}")
    public TaskRecord runRoutePreset(@PathVariable UUID id, @PathVariable UUID presetId) {
        return botFleetService.enqueueRoutePreset(id, presetId);
    }

    public record UpsertBotRequest(String name, UUID accountId, String clientInstallPath, String preferredCharacter,
                                   String preferredWorld, String profileName, String launchCommand) {
    }

    public record RunActionRequest(String actionType, JsonNode params) {
    }

    public record ScreenshotStreamRequest(Integer intervalSeconds, Integer intervalMillis) {
        public int resolveIntervalMillis(int fallbackMillis) {
            if (intervalMillis != null && intervalMillis > 0) {
                return intervalMillis;
            }
            if (intervalSeconds != null && intervalSeconds > 0) {
                return intervalSeconds * 1000;
            }
            return fallbackMillis;
        }
    }

    public record WebRtcOfferRequest(String sdp, String type) {
    }

    public record ReplaySaveRequest(Integer requestedSeconds, String reason) {
    }
}
