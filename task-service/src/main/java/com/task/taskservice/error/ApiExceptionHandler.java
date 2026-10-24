package com.task.taskservice.error;

import java.time.Instant;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    ResponseEntity<ApiError> taskNotFound(TaskNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({StaleTaskVersionException.class, OptimisticLockingFailureException.class})
    ResponseEntity<ApiError> staleTaskVersion() {
        return error(
                HttpStatus.CONFLICT, "STALE_TASK_VERSION", "Task was modified by another request");
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ResponseEntity<ApiError> bodyValidation(WebExchangeBindException exception) {
        String message = exception.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> parameterValidation() {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request parameter");
    }

    @ExceptionHandler(ServerWebInputException.class)
    ResponseEntity<ApiError> invalidInput() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message));
    }
}
