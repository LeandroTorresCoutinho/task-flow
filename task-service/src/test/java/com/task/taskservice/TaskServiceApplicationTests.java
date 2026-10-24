package com.task.taskservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.task.taskservice.task.Task;
import com.task.taskservice.task.TaskRepository;
import com.task.taskservice.task.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskServiceApplicationTests {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("task_flow");

    @LocalServerPort
    int port;

    @Autowired
    TaskRepository tasks;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> r2dbcUrl("task_service"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.liquibase.url", postgres::getJdbcUrl);
        registry.add("spring.liquibase.user", postgres::getUsername);
        registry.add("spring.liquibase.password", postgres::getPassword);
    }

    @Test
    void healthEndpointIsUp() {
        WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void savedTaskCanBeUpdated() {
        var ownerId = UUID.randomUUID();
        var createdAt = Instant.parse("2026-10-23T12:00:00Z");
        var created = Task.create(
                UUID.randomUUID(),
                ownerId,
                "Initial title",
                "Initial description",
                TaskStatus.TODO,
                LocalDate.parse("2026-10-30"),
                createdAt);
        Task saved = tasks.save(created).block();
        var updatedAt = createdAt.plusSeconds(60);
        var changed = new Task(
                saved.id(),
                saved.ownerId(),
                "Updated title",
                saved.description(),
                TaskStatus.IN_PROGRESS,
                saved.dueDate(),
                saved.createdAt(),
                updatedAt,
                saved.version());

        Task updated = tasks.save(changed).block();

        assertEquals("Updated title", updated.title());
        assertEquals(TaskStatus.IN_PROGRESS, updated.status());
        assertEquals(updatedAt, updated.updatedAt());
        assertEquals(saved.version() + 1, updated.version());
        assertEquals(1L, tasks.count().block());
    }

    private static String r2dbcUrl(String schema) {
        return "r2dbc:postgresql://%s:%d/%s?schema=%s"
                .formatted(postgres.getHost(), postgres.getMappedPort(5432), postgres.getDatabaseName(), schema);
    }
}
