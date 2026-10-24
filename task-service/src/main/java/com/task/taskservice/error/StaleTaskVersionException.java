package com.task.taskservice.error;

public class StaleTaskVersionException extends RuntimeException {

    public StaleTaskVersionException() {
        super("Task was modified by another request");
    }
}
