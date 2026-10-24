package com.task.taskservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.task.taskservice.task.Task;
import com.task.taskservice.task.TaskRepository;
import com.task.taskservice.task.TaskStatus;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Mono;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TaskServiceApplicationTests.TestJwtConfiguration.class)
class TaskServiceApplicationTests {

    private static final UUID USER_A_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

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
        long taskCountBeforeTest = tasks.count().block();
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
        assertEquals(taskCountBeforeTest + 1, tasks.count().block());
    }

    @Test
    void userCanCreateAndReadTaskThroughHttp() {
        SuiteResult result = Runner.path("classpath:features/task-create-read.feature")
                .systemProperty("task.base-url", "http://localhost:" + port)
                .parallel(1);

        assertTrue(result.isPassed());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestJwtConfiguration {

        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            return token -> {
                UUID ownerId = switch (token) {
                    case "user-a-token" -> USER_A_ID;
                    case "user-b-token" -> USER_B_ID;
                    default -> throw new IllegalArgumentException("Unknown test token");
                };
                return Mono.just(Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject(ownerId.toString())
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build());
            };
        }
    }

    private static String r2dbcUrl(String schema) {
        return "r2dbc:postgresql://%s:%d/%s?schema=%s"
                .formatted(postgres.getHost(), postgres.getMappedPort(5432), postgres.getDatabaseName(), schema);
    }
}
