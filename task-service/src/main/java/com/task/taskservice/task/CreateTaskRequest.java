package com.task.taskservice.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @NotNull TaskStatus status,
        @NotNull LocalDate dueDate) {}
