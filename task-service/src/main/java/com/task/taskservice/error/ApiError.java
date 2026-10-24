package com.task.taskservice.error;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String code, String message) {}
