package com.example.notificationservice.controller;

import com.example.notificationservice.service.WebSocketNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "WebSocket Notification API", description = "WebSocket API для реального времени уведомлений")
public class WebSocketController {

    private final WebSocketNotificationService webSocketNotificationService;

    @MessageMapping("/notifications.subscribe")
    @SendToUser("/queue/notifications")
    @Operation(summary = "Подписка на уведомления", description = "Подписывает пользователя на получение уведомлений по WebSocket")
    public String subscribe(Principal principal) {
        if (principal == null) {
            log.warn("WebSocket subscription attempt without authentication");
            throw new AccessDeniedException("User not authenticated");
        }

        String userId = principal.getName();
        log.info("User {} subscribed to notifications", userId);
        return "SUBSCRIBED_SUCCESS";
    }

    @MessageMapping("/notifications.markRead")
    @Operation(summary = "Отметить уведомление как прочитанное", description = "Отмечает уведомление как прочитанное через WebSocket")
    public void markNotificationAsRead(UUID notificationId, Principal principal) {
        if (principal == null) {
            log.warn("WebSocket markRead attempt without authentication");
            throw new AccessDeniedException("User not authenticated");
        }

        String userId = principal.getName();
        log.info("User {} marking notification {} as read via WebSocket", userId, notificationId);

        try {
            UUID userUUID = UUID.fromString(userId);
            webSocketNotificationService.markAsReadAndNotify(notificationId, userUUID);
        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID format in WebSocket: {}", userId);
            throw new IllegalArgumentException("Invalid user ID format");
        }
    }
}