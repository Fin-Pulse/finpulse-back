package com.example.notificationservice.exception;

import java.util.UUID;

public class NotificationAccessDeniedException extends RuntimeException {
    public NotificationAccessDeniedException(String message) {
        super(message);
    }
    
    public NotificationAccessDeniedException(UUID notificationId, UUID userId) {
        super("Notification " + notificationId + " does not belong to user " + userId);
    }
}

