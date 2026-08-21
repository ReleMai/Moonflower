package io.havenbot.server.api;

import io.havenbot.server.model.MediaClipRecord;
import io.havenbot.server.service.MediaClipService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clips")
public class ClipsController {
    private final MediaClipService mediaClipService;

    public ClipsController(MediaClipService mediaClipService) {
        this.mediaClipService = mediaClipService;
    }

    @GetMapping
    public List<MediaClipRecord> list(@RequestParam(required = false) UUID botId) {
        return mediaClipService.list(botId);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID id) {
        MediaClipRecord record = mediaClipService.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(record.mediaType()))
                .body(mediaClipService.load(record));
    }
}
