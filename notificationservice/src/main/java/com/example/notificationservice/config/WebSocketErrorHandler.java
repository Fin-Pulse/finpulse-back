package com.example.notificationservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class WebSocketErrorHandler {

    @MessageExceptionHandler(AccessDeniedException.class)
    @SendToUser("/queue/errors")
    public Map<String, String> handleAccessDenied(AccessDeniedException ex) {
        log.warn("WebSocket access denied: {}", ex.getMessage());

        Map<String, String> error = new HashMap<>();
        error.put("error", "ACCESS_DENIED");
        error.put("message", "Authentication required");
        return error;
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser("/queue/errors")
    public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("WebSocket invalid argument: {}", ex.getMessage());

        Map<String, String> error = new HashMap<>();
        error.put("error", "INVALID_INPUT");
        error.put("message", ex.getMessage());
        return error;
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public Map<String, String> handleGenericException(Exception ex) {
        log.error("WebSocket error: {}", ex.getMessage(), ex);

        Map<String, String> error = new HashMap<>();
        error.put("error", "INTERNAL_ERROR");
        error.put("message", "An internal error occurred");
        return error;
    }
}