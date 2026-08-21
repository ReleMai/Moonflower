package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.server.model.ScreenshotRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

@Service
public class LiveFeedService {
    public record LiveFrame(byte[] content, String mediaType, Instant createdAt, int width, int height, JsonNode metadata) {
    }

    private static final byte[] FRAME_BOUNDARY = "--frame\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FRAME_TRAILER = "\r\n".getBytes(StandardCharsets.UTF_8);

    private final ScreenshotService screenshotService;
    private final ObjectMapper objectMapper;
    private final Map<UUID, LiveFrame> latestFrames = new ConcurrentHashMap<>();
    private final Map<UUID, Set<BlockingDeque<LiveFrame>>> subscribers = new ConcurrentHashMap<>();

    public LiveFeedService(ScreenshotService screenshotService, ObjectMapper objectMapper) {
        this.screenshotService = screenshotService;
        this.objectMapper = objectMapper;
    }

    public LiveFrame publishFrame(UUID botId, JsonNode metadata, String base64Content) {
        byte[] content = Base64.getDecoder().decode(base64Content);
        LiveFrame frame = new LiveFrame(
                content,
                metadata.path("mediaType").asText("image/jpeg"),
                parseInstant(metadata.path("createdAt").asText(null)),
                metadata.path("width").asInt(0),
                metadata.path("height").asInt(0),
                metadata
        );
        latestFrames.put(botId, frame);
        for (BlockingDeque<LiveFrame> queue : subscribers.getOrDefault(botId, Set.of())) {
            queue.clear();
            queue.offer(frame);
        }
        return frame;
    }

    public Optional<LiveFrame> latestFrame(UUID botId) {
        return Optional.ofNullable(latestFrames.get(botId));
    }

    public void stream(UUID botId, OutputStream outputStream) throws IOException {
        BlockingDeque<LiveFrame> queue = new LinkedBlockingDeque<>(2);
        subscribers.computeIfAbsent(botId, ignored -> ConcurrentHashMap.newKeySet()).add(queue);
        try {
            LiveFrame latest = latestFrames.get(botId);
            if (latest != null) {
                writeFrame(outputStream, latest);
                outputStream.flush();
            }

            while (!Thread.currentThread().isInterrupted()) {
                LiveFrame frame = queue.poll(45, TimeUnit.SECONDS);
                if (frame == null) {
                    continue;
                }
                writeFrame(outputStream, frame);
                outputStream.flush();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            Set<BlockingDeque<LiveFrame>> botSubscribers = subscribers.get(botId);
            if (botSubscribers != null) {
                botSubscribers.remove(queue);
                if (botSubscribers.isEmpty()) {
                    subscribers.remove(botId);
                }
            }
        }
    }

    public ScreenshotRecord saveCurrentFrame(UUID botId) {
        LiveFrame frame = latestFrames.get(botId);
        if (frame == null) {
            throw new IllegalStateException("No live frame is available to save for this bot.");
        }
        JsonNode metadata = frame.metadata() == null || frame.metadata().isMissingNode()
                ? defaultMetadata(frame)
                : frame.metadata();
        return screenshotService.storeSaved(botId, metadata, frame.content());
    }

    public void clearBot(UUID botId) {
        latestFrames.remove(botId);
    }

    private void writeFrame(OutputStream outputStream, LiveFrame frame) throws IOException {
        outputStream.write(FRAME_BOUNDARY);
        outputStream.write(("Content-Type: " + frame.mediaType() + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Length: " + frame.content().length + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(("X-Created-At: " + frame.createdAt() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(frame.content());
        outputStream.write(FRAME_TRAILER);
    }

    private JsonNode defaultMetadata(LiveFrame frame) {
        return objectMapper.createObjectNode()
                .put("mediaType", frame.mediaType())
                .put("createdAt", frame.createdAt().toString())
                .put("width", frame.width())
                .put("height", frame.height())
                .put("captureSource", "live-feed");
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.now();
        }
    }
}
