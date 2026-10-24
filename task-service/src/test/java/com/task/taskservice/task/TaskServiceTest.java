package com.task.taskservice.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.task.taskservice.error.StaleTaskVersionException;
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

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00.123456789Z");
    private static final Instant DATABASE_NOW = Instant.parse("2026-10-23T12:00:00.123456Z");

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
        assertEquals(DATABASE_NOW, savedTask.getValue().createdAt());
        assertEquals(DATABASE_NOW, savedTask.getValue().updatedAt());
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

    @Test
    void updatesOwnedTaskAndPreservesServerManagedFields() {
        UUID ownerId = UUID.randomUUID();
        Task task = task(ownerId);
        var request = new UpdateTaskRequest(
                "Ship release", "Deploy the release", TaskStatus.DONE, LocalDate.parse("2026-11-01"), task.version());
        when(tasks.findByIdAndOwnerId(task.id(), ownerId)).thenReturn(Mono.just(task));
        when(tasks.save(any(Task.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        TaskResponse response = service.update(ownerId, task.id(), request).block();

        var savedTask = ArgumentCaptor.forClass(Task.class);
        verify(tasks).save(savedTask.capture());
        assertEquals(task.id(), savedTask.getValue().id());
        assertEquals(ownerId, savedTask.getValue().ownerId());
        assertEquals(task.createdAt(), savedTask.getValue().createdAt());
        assertEquals(DATABASE_NOW, savedTask.getValue().updatedAt());
        assertEquals(task.version(), savedTask.getValue().version());
        assertEquals(request.title(), response.title());
        assertEquals(request.description(), response.description());
        assertEquals(request.status(), response.status());
        assertEquals(request.dueDate(), response.dueDate());
    }

    @Test
    void rejectsStaleUpdateWithoutSaving() {
        UUID ownerId = UUID.randomUUID();
        Task task = task(ownerId);
        var request = new UpdateTaskRequest(
                "Ship release", "Deploy the release", TaskStatus.DONE, LocalDate.parse("2026-11-01"), 1L);
        when(tasks.findByIdAndOwnerId(task.id(), ownerId)).thenReturn(Mono.just(task));

        assertThrows(StaleTaskVersionException.class, () -> service.update(ownerId, task.id(), request).block());
        verify(tasks, never()).save(any(Task.class));
    }

    @Test
    void hidesTaskWhenUpdateOwnerDoesNotMatch() {
        UUID taskId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        var request = new UpdateTaskRequest(
                "Ship release", "Deploy the release", TaskStatus.DONE, LocalDate.parse("2026-11-01"), 0L);
        when(tasks.findByIdAndOwnerId(taskId, otherOwnerId)).thenReturn(Mono.empty());

        assertThrows(TaskNotFoundException.class, () -> service.update(otherOwnerId, taskId, request).block());
        verify(tasks, never()).save(any(Task.class));
    }

    @Test
    void deletesOwnedTask() {
        UUID ownerId = UUID.randomUUID();
        Task task = task(ownerId);
        when(tasks.findByIdAndOwnerId(task.id(), ownerId)).thenReturn(Mono.just(task));
        when(tasks.deleteByIdAndOwnerIdAndVersion(task.id(), ownerId, task.version())).thenReturn(Mono.just(1L));

        service.delete(ownerId, task.id()).block();

        verify(tasks).deleteByIdAndOwnerIdAndVersion(task.id(), ownerId, task.version());
    }

    @Test
    void rejectsDeleteWhenTaskChangesAfterLookup() {
        UUID ownerId = UUID.randomUUID();
        Task task = task(ownerId);
        when(tasks.findByIdAndOwnerId(task.id(), ownerId)).thenReturn(Mono.just(task));
        when(tasks.deleteByIdAndOwnerIdAndVersion(task.id(), ownerId, task.version())).thenReturn(Mono.just(0L));

        assertThrows(StaleTaskVersionException.class, () -> service.delete(ownerId, task.id()).block());
    }

    @Test
    void hidesTaskWhenDeleteOwnerDoesNotMatch() {
        UUID taskId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        when(tasks.findByIdAndOwnerId(taskId, otherOwnerId)).thenReturn(Mono.empty());

        assertThrows(TaskNotFoundException.class, () -> service.delete(otherOwnerId, taskId).block());
        verify(tasks, never()).deleteByIdAndOwnerIdAndVersion(any(UUID.class), any(UUID.class), any(Long.class));
    }

    private Task task(UUID ownerId) {
        return new Task(
                UUID.randomUUID(),
                ownerId,
                "Prepare release",
                "Complete the checklist",
                TaskStatus.TODO,
                LocalDate.parse("2026-10-30"),
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                0L);
    }
}
