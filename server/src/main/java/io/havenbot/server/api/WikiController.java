package io.havenbot.server.api;

import io.havenbot.server.service.WikiLookupService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/wiki")
public class WikiController {
    private final WikiLookupService wikiLookupService;

    public WikiController(WikiLookupService wikiLookupService) {
        this.wikiLookupService = wikiLookupService;
    }

    @GetMapping("/detail")
    public WikiLookupService.WikiDetail detail(@RequestParam String label,
                                               @RequestParam(defaultValue = "") String kind,
                                               @RequestParam(defaultValue = "") String wikiTitle,
                                               @RequestParam(defaultValue = "") String wikiSection,
                                               @RequestParam(defaultValue = "") String wikiUrl) {
        return wikiLookupService.lookup(label, kind, wikiTitle, wikiSection, wikiUrl);
    }

    @GetMapping("/icon")
    public ResponseEntity<FileSystemResource> icon(@RequestParam String label,
                                                   @RequestParam(defaultValue = "") String kind,
                                                   @RequestParam(defaultValue = "") String wikiTitle,
                                                   @RequestParam(defaultValue = "") String wikiSection,
                                                   @RequestParam(defaultValue = "") String wikiUrl) {
        WikiLookupService.WikiIcon icon = wikiLookupService.lookupIcon(label, kind, wikiTitle, wikiSection, wikiUrl);
        if (icon == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .contentType(MediaType.parseMediaType(icon.contentType()))
                .body(new FileSystemResource(icon.path()));
    }
}
