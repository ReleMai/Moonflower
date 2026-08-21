package io.havenbot.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.havenbot.protocol.TaskStatus;
import io.havenbot.server.model.TaskRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskServiceTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TaskService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("task-service-test.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        service = new TaskService(new JdbcTemplate(dataSource), objectMapper);
    }

    @Test
    void enqueueAndTransitionTaskLifecycle() throws Exception {
        UUID botId = UUID.randomUUID();
        JsonNode params = objectMapper.readTree("{\"option\":\"sort\"}");

        TaskRecord record = service.enqueue(botId, "inventory.sort", params);
        Optional<TaskRecord> queued = service.nextQueued(botId);

        assertTrue(queued.isPresent());
        assertEquals(record.id(), queued.get().id());
        assertEquals(TaskStatus.QUEUED, queued.get().status());

        service.markDispatched(record.id());
        service.markStarted(record.id());
        service.markCompleted(record.id());

        TaskRecord updated = service.listForBot(botId).get(0);
        assertEquals(TaskStatus.COMPLETED, updated.status());
        assertEquals("inventory.sort", updated.actionType());
        assertEquals("sort", updated.params().get("option").asText());
    }

    @Test
    void interruptMarksQueuedDispatchedAndRunningTasksForBot() throws Exception {
        UUID botId = UUID.randomUUID();
        JsonNode params = objectMapper.readTree("{\"checkpoints\":[]}");
        TaskRecord queued = service.enqueue(botId, "route.start", params);
        TaskRecord running = service.enqueue(botId, "cleanup.start", params);

        service.markDispatched(running.id());
        service.markStarted(running.id());
        service.markInterruptedForBot(botId, "bot disconnected");

        for (TaskRecord record : service.listForBot(botId)) {
            if (record.id().equals(queued.id()) || record.id().equals(running.id())) {
                assertEquals(TaskStatus.INTERRUPTED, record.status());
                assertEquals("bot disconnected", record.errorMessage());
            }
        }
    }

    @Test
    void interruptActiveLeavesQueuedTasksIntact() throws Exception {
        UUID botId = UUID.randomUUID();
        JsonNode params = objectMapper.readTree("{\"checkpoints\":[]}");
        TaskRecord queued = service.enqueue(botId, "route.start", params);
        TaskRecord running = service.enqueue(botId, "cleanup.start", params);

        service.markDispatched(running.id());
        service.markStarted(running.id());
        service.markActiveInterruptedForBot(botId, "paused");

        for (TaskRecord record : service.listForBot(botId)) {
            if (record.id().equals(queued.id())) {
                assertEquals(TaskStatus.QUEUED, record.status());
            }
            if (record.id().equals(running.id())) {
                assertEquals(TaskStatus.INTERRUPTED, record.status());
                assertEquals("paused", record.errorMessage());
            }
        }
    }
}
