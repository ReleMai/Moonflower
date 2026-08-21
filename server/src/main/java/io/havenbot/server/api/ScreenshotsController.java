package io.havenbot.server.api;

import io.havenbot.server.model.ScreenshotRecord;
import io.havenbot.server.service.ScreenshotService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/screenshots")
public class ScreenshotsController {
    private final ScreenshotService screenshotService;

    public ScreenshotsController(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
    }

    @GetMapping
    public List<ScreenshotRecord> list(@RequestParam(required = false) UUID botId) {
        return screenshotService.list(botId);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID id) {
        ScreenshotRecord record = screenshotService.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(record.mediaType()))
                .body(screenshotService.load(record));
    }
}
