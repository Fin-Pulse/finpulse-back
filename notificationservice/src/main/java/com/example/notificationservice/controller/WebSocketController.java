package com.example.notificationservice.controller;

import com.example.notificationservice.dto.NotificationDto;
import com.example.notificationservice.service.WebSocketNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WebSocket Notification API", description = "WebSocket API для реального времени уведомлений")
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketNotificationService webSocketNotificationService;

    @MessageMapping("/notifications.subscribe")
    @SendToUser("/queue/notifications")
    public String subscribe(Principal principal) {
        String userId = principal.getName();
        log.info("User {} subscribed to notifications", userId);
        return "SUBSCRIBED_SUCCESS";
    }

    @MessageMapping("/notifications.markRead")
    public void markNotificationAsRead(UUID notificationId, Principal principal) {
        String userId = principal.getName();
        log.info("User {} marking notification {} as read", userId, notificationId);
        webSocketNotificationService.markAsReadAndNotify(notificationId, UUID.fromString(userId));
    }
}