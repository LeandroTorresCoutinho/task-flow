package com.task.taskservice.task;

import java.time.LocalDate;

public record CreateTaskRequest(String title, String description, TaskStatus status, LocalDate dueDate) {}
