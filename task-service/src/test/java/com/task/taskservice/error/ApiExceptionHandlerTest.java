package com.task.taskservice.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    @Test
    void returnsConsistentErrorForStaleTaskVersion() {
        ResponseEntity<ApiError> response = new ApiExceptionHandler().staleTaskVersion();

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().status());
        assertEquals("STALE_TASK_VERSION", response.getBody().code());
        assertEquals("Task was modified by another request", response.getBody().message());
    }
}
