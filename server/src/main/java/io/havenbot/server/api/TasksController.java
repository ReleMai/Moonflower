package io.havenbot.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.havenbot.server.model.TaskRecord;
import io.havenbot.server.service.BotFleetService;
import io.havenbot.server.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TasksController {
    private final TaskService taskService;
    private final BotFleetService botFleetService;

    public TasksController(TaskService taskService, BotFleetService botFleetService) {
        this.taskService = taskService;
        this.botFleetService = botFleetService;
    }

    @GetMapping
    public List<TaskRecord> list(@RequestParam(required = false) UUID botId) {
        return botId == null ? taskService.list() : taskService.listForBot(botId);
    }

    @PostMapping
    public TaskRecord create(@RequestBody CreateTaskRequest request) {
        return botFleetService.enqueueAction(request.botId(), request.actionType(), request.params());
    }

    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable UUID id) {
        botFleetService.cancelTask(id);
    }

    public record CreateTaskRequest(UUID botId, String actionType, JsonNode params) {
    }
}
