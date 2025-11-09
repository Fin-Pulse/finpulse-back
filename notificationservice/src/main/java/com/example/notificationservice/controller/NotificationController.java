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


    @Operation(summary = "Получить все уведомления пользователя",
            description = "Возвращает список всех уведомлений пользователя, отсортированных по дате создания (новые сначала)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешное получение списка уведомлений"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Notification>> getUserNotifications(
            @Parameter(description = "UUID пользователя", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID userId) {
        List<Notification> notifications = notificationService.getUserNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    @Operation(summary = "Получить непрочитанные уведомления",
            description = "Возвращает список непрочитанных уведомлений пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешное получение непрочитанных уведомлений"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @GetMapping("/user/{userId}/unread")
    public ResponseEntity<List<Notification>> getUnreadNotifications(
            @Parameter(description = "UUID пользователя", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID userId) {
        List<Notification> notifications = notificationService.getUnreadNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    @Operation(summary = "Получить количество непрочитанных уведомлений",
            description = "Возвращает количество непрочитанных уведомлений пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешное получение количества"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @GetMapping("/user/{userId}/unread-count")
    public ResponseEntity<Long> getUnreadCount(
            @Parameter(description = "UUID пользователя", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID userId) {
        Long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(count);
    }

    @Operation(summary = "Отметить уведомление как прочитанное",
            description = "Отмечает уведомление как прочитанное по его ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Уведомление успешно отмечено как прочитанное"),
            @ApiResponse(responseCode = "404", description = "Уведомление не найдено")
    })
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Notification> markAsRead(
            @Parameter(description = "UUID уведомления", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID notificationId) {
        Notification notification = notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(notification);
    }


    @Operation(summary = "Создать новое уведомление",
            description = "Создает новое уведомление для пользователя и отправляет через WebSocket")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Уведомление успешно создано"),
            @ApiResponse(responseCode = "400", description = "Неверные данные запроса")
    })
    @PostMapping
    public ResponseEntity<Notification> createNotification(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Данные для создания уведомления")
            @RequestBody CreateNotificationRequest request) {
        Notification notification = notificationCoordinator.createNotificationWithWebSocket(request);
        return ResponseEntity.ok(notification);
    }
}