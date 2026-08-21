package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class WebRtcGatewayService {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;
    private final String gatewayBaseUrl;

    public WebRtcGatewayService(ObjectMapper objectMapper,
                                @Value("${havenbot.webrtc.gateway-url:http://127.0.0.1:8091}") String gatewayBaseUrl) {
        this.objectMapper = objectMapper;
        this.gatewayBaseUrl = gatewayBaseUrl;
    }

    public JsonNode createAnswer(UUID botId, String operatorToken, String sdp, String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("botId", botId.toString());
        payload.put("operatorToken", operatorToken);
        payload.put("sdp", sdp);
        payload.put("type", type);
        return postJson("/api/webrtc/offer", payload);
    }

    public JsonNode health() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gatewayBaseUrl + "/health"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("WebRTC gateway health check failed with " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("WebRTC gateway health check failed: " + ex.getMessage(), ex);
        }
    }

    public void ensureReplay(UUID botId) {
        postJson("/api/replay/ensure", Map.of("botId", botId.toString()));
    }

    public void releaseReplay(UUID botId) {
        postJson("/api/replay/release", Map.of("botId", botId.toString()));
    }

    public JsonNode saveReplay(UUID botId, String triggerType, String reason, Integer requestedSeconds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("botId", botId.toString());
        payload.put("triggerType", triggerType);
        payload.put("reason", reason == null ? "" : reason);
        if (requestedSeconds != null && requestedSeconds > 0) {
            payload.put("requestedSeconds", requestedSeconds);
        }
        return postJson("/api/replay/save", payload);
    }

    private JsonNode postJson(String path, Map<String, Object> payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayBaseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(20))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("WebRTC gateway request failed with " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("WebRTC gateway request failed: " + ex.getMessage(), ex);
        }
    }
}
