package com.task.taskservice.task;

import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TaskRepository extends ReactiveCrudRepository<Task, UUID> {

    Mono<Task> findByIdAndOwnerId(UUID id, UUID ownerId);

    Flux<Task> findByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId, Limit limit);
}
