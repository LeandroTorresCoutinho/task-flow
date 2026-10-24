package com.task.taskservice.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("tasks")
public record Task(
        @Id UUID id,
        @Column("owner_id") UUID ownerId,
        String title,
        String description,
        TaskStatus status,
        @Column("due_date") LocalDate dueDate,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt,
        @Version Long version) {

    public static Task create(
            UUID id,
            UUID ownerId,
            String title,
            String description,
            TaskStatus status,
            LocalDate dueDate,
            Instant createdAt) {
        return new Task(id, ownerId, title, description, status, dueDate, createdAt, createdAt, null);
    }
}
