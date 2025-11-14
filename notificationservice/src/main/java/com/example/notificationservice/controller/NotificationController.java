package com.example.notificationservice.controller;

import com.example.notificationservice.dto.CreateNotificationRequest;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.service.NotificationCoordinator;
import com.example.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification API", description = "API для управления уведомлениями пользователей")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationCoordinator notificationCoordinator;

    @Operation(summary = "Получить все уведомления пользователя")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Notification>> getUserNotifications(@PathVariable String userId) {
        List<Notification> notifications = notificationService.getUserNotifications(UUID.fromString(userId));
        return ResponseEntity.ok(notifications);
    }

    @Operation(summary = "Получить непрочитанные уведомления")
    @GetMapping("/user/{userId}/unread")
    public ResponseEntity<List<Notification>> getUnreadNotifications(@PathVariable String userId) {
        List<Notification> notifications = notificationService.getUnreadNotifications(UUID.fromString(userId));
        return ResponseEntity.ok(notifications);
    }

    @Operation(summary = "Получить количество непрочитанных уведомлений")
    @GetMapping("/user/{userId}/unread-count")
    public ResponseEntity<Long> getUnreadCount(@PathVariable String userId) {
        Long count = notificationService.getUnreadCount(UUID.fromString(userId));
        return ResponseEntity.ok(count);
    }

    @Operation(summary = "Отметить уведомление как прочитанное")
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable UUID notificationId) {
        Notification notification = notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(notification);
    }

    @Operation(summary = "Создать новое уведомление")
    @PostMapping
    public ResponseEntity<Notification> createNotification(@RequestBody CreateNotificationRequest request) {
        Notification notification = notificationCoordinator.createNotificationWithWebSocket(request);
        return ResponseEntity.ok(notification);
    }
}