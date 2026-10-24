package com.task.taskservice.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.task.taskservice.error.TaskNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    private final TaskRepository tasks = mock(TaskRepository.class);
    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(tasks, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsTaskForAuthenticatedOwner() {
        UUID ownerId = UUID.randomUUID();
        var request = new CreateTaskRequest(
                "Prepare release", "Complete the checklist", TaskStatus.TODO, LocalDate.parse("2026-10-30"));
        when(tasks.save(any(Task.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        TaskResponse response = service.create(ownerId, request).block();

        var savedTask = ArgumentCaptor.forClass(Task.class);
        verify(tasks).save(savedTask.capture());
        assertEquals(ownerId, savedTask.getValue().ownerId());
        assertEquals(NOW, savedTask.getValue().createdAt());
        assertEquals(NOW, savedTask.getValue().updatedAt());
        assertNotNull(response.id());
    }

    @Test
    void retrievesTaskUsingBothIdAndOwnerId() {
        UUID ownerId = UUID.randomUUID();
        Task task = task(ownerId);
        when(tasks.findByIdAndOwnerId(task.id(), ownerId)).thenReturn(Mono.just(task));

        TaskResponse response = service.get(ownerId, task.id()).block();

        assertEquals(task.id(), response.id());
        verify(tasks).findByIdAndOwnerId(task.id(), ownerId);
    }

    @Test
    void hidesTaskWhenOwnerDoesNotMatch() {
        UUID taskId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        when(tasks.findByIdAndOwnerId(taskId, otherOwnerId)).thenReturn(Mono.empty());

        assertThrows(TaskNotFoundException.class, () -> service.get(otherOwnerId, taskId).block());
    }

    @Test
    void listsOnlyOwnerTasksWithRequestedBound() {
        UUID ownerId = UUID.randomUUID();
        when(tasks.findByOwnerIdOrderByCreatedAtDescIdDesc(any(UUID.class), any(Limit.class)))
                .thenReturn(Flux.just(task(ownerId)));

        service.list(ownerId, 25).collectList().block();

        var limit = ArgumentCaptor.forClass(Limit.class);
        verify(tasks).findByOwnerIdOrderByCreatedAtDescIdDesc(org.mockito.ArgumentMatchers.eq(ownerId), limit.capture());
        assertEquals(25, limit.getValue().max());
    }

    @Test
    void rejectsUnboundedListing() {
        assertThrows(IllegalArgumentException.class, () -> service.list(UUID.randomUUID(), 101).blockLast());
        verify(tasks, never()).findByOwnerIdOrderByCreatedAtDescIdDesc(any(UUID.class), any(Limit.class));
    }

    private Task task(UUID ownerId) {
        return new Task(
                UUID.randomUUID(),
                ownerId,
                "Prepare release",
                "Complete the checklist",
                TaskStatus.TODO,
                LocalDate.parse("2026-10-30"),
                NOW,
                NOW,
                0L);
    }
}
