package io.havenbot.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.havenbot.server.model.TaskPresetRecord;
import io.havenbot.server.service.TaskPresetService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/task-presets")
public class TaskPresetsController {
    private final TaskPresetService taskPresetService;

    public TaskPresetsController(TaskPresetService taskPresetService) {
        this.taskPresetService = taskPresetService;
    }

    @GetMapping
    public List<TaskPresetRecord> list() {
        return taskPresetService.list();
    }

    @PostMapping
    public TaskPresetRecord create(@RequestBody UpsertTaskPresetRequest request) {
        return taskPresetService.create(request.name(), request.actionType(), request.params());
    }

    @PutMapping("/{id}")
    public TaskPresetRecord update(@PathVariable UUID id, @RequestBody UpsertTaskPresetRequest request) {
        return taskPresetService.update(id, request.name(), request.actionType(), request.params());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        taskPresetService.delete(id);
    }

    public record UpsertTaskPresetRequest(String name, String actionType, JsonNode params) {
    }
}
