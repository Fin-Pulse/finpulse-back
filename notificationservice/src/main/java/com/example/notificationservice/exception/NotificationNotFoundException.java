package com.example.notificationservice.exception;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(String message) {
        super(message);
    }
    
    public NotificationNotFoundException(UUID notificationId) {
        super("Notification not found with id: " + notificationId);
    }
}

