package com.task.taskservice.task;

import static com.task.taskservice.task.TaskService.MAX_LIST_LIMIT;

import com.task.taskservice.config.JwtIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @PostMapping
    public Mono<ResponseEntity<TaskResponse>> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTaskRequest request) {
        return tasks.create(JwtIdentity.ownerId(jwt), request)
                .map(task -> ResponseEntity.created(URI.create("/api/v1/tasks/" + task.id())).body(task));
    }

    @GetMapping("/{taskId}")
    public Mono<TaskResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId) {
        return tasks.get(JwtIdentity.ownerId(jwt), taskId);
    }

    @GetMapping
    public Flux<TaskResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_LIST_LIMIT) int limit) {
        return tasks.list(JwtIdentity.ownerId(jwt), limit);
    }
}
