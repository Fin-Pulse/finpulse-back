package com.example.aggregationservice.service;

import com.example.aggregationservice.model.ScheduledTask;

public interface TaskHandler {
    String getSupportedTaskType();
    void handle(ScheduledTask task);
    default boolean shouldDeleteAfterSuccess() {
        return false;
    }
}