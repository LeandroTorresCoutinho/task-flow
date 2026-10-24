package com.task.taskservice.task;

import com.task.taskservice.error.StaleTaskVersionException;
import com.task.taskservice.error.TaskNotFoundException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TaskService {

    public static final int DEFAULT_LIST_LIMIT = 20;
    public static final int MAX_LIST_LIMIT = 100;

    private final TaskRepository tasks;
    private final Clock clock;

    @Autowired
    public TaskService(TaskRepository tasks) {
        this(tasks, Clock.systemUTC());
    }

    TaskService(TaskRepository tasks, Clock clock) {
        this.tasks = tasks;
        this.clock = clock;
    }

    public Mono<TaskResponse> create(UUID ownerId, CreateTaskRequest request) {
        var task = Task.create(
                UUID.randomUUID(),
                ownerId,
                request.title(),
                request.description(),
                request.status(),
                request.dueDate(),
                clock.instant());
        return tasks.save(task).map(TaskResponse::from);
    }

    public Mono<TaskResponse> get(UUID ownerId, UUID taskId) {
        return findOwned(ownerId, taskId).map(TaskResponse::from);
    }

    public Flux<TaskResponse> list(UUID ownerId, int limit) {
        if (limit < 1 || limit > MAX_LIST_LIMIT) {
            return Flux.error(new IllegalArgumentException("limit must be between 1 and " + MAX_LIST_LIMIT));
        }
        return tasks.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId, Limit.of(limit)).map(TaskResponse::from);
    }

    public Mono<TaskResponse> update(UUID ownerId, UUID taskId, UpdateTaskRequest request) {
        return findOwned(ownerId, taskId)
                .flatMap(task -> {
                    if (!Objects.equals(task.version(), request.version())) {
                        return Mono.error(new StaleTaskVersionException());
                    }
                    var updated = new Task(
                            task.id(),
                            task.ownerId(),
                            request.title(),
                            request.description(),
                            request.status(),
                            request.dueDate(),
                            task.createdAt(),
                            clock.instant(),
                            task.version());
                    return tasks.save(updated);
                })
                .map(TaskResponse::from);
    }

    public Mono<Void> delete(UUID ownerId, UUID taskId) {
        return findOwned(ownerId, taskId)
                .flatMap(tasks::delete);
    }

    private Mono<Task> findOwned(UUID ownerId, UUID taskId) {
        return tasks.findByIdAndOwnerId(taskId, ownerId)
                .switchIfEmpty(Mono.error(TaskNotFoundException::new));
    }
}
