package io.havenbot.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.havenbot.server.model.RoutePresetRecord;
import io.havenbot.server.service.RoutePresetService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/routes")
public class RoutesController {
    private final RoutePresetService routePresetService;

    public RoutesController(RoutePresetService routePresetService) {
        this.routePresetService = routePresetService;
    }

    @GetMapping
    public List<RoutePresetRecord> list() {
        return routePresetService.list();
    }

    @PostMapping
    public RoutePresetRecord create(@RequestBody CreateRoutePresetRequest request) {
        return routePresetService.create(request.name(), request.route());
    }

    @PutMapping("/{id}")
    public RoutePresetRecord update(@PathVariable UUID id, @RequestBody CreateRoutePresetRequest request) {
        return routePresetService.update(id, request.name(), request.route());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        routePresetService.delete(id);
    }

    public record CreateRoutePresetRequest(String name, JsonNode route) {
    }
}
