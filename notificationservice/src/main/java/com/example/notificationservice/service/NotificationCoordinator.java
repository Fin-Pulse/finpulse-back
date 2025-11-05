package com.example.notificationservice.service;

import com.example.notificationservice.dto.CreateNotificationRequest;
import com.example.notificationservice.dto.NotificationDto;
import com.example.notificationservice.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCoordinator {

    private final NotificationService notificationService;
    private final WebSocketNotificationService webSocketNotificationService;

    @Transactional
    public Notification createNotificationWithWebSocket(CreateNotificationRequest request) {
        // Создаем уведомление через NotificationService
        Notification notification = notificationService.createNotification(request);

        // Отправляем через WebSocket
        try {
            NotificationDto dto = convertToDto(notification);
            webSocketNotificationService.sendNotificationToUser(notification.getUserId(), dto);
            log.info("Real-time notification sent to user: {}", notification.getUserId());
        } catch (Exception e) {
            log.error("Failed to send real-time notification: {}", e.getMessage());
            // Продолжаем выполнение даже если WebSocket не работает
        }

        return notification;
    }

    private NotificationDto convertToDto(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId());
        dto.setUserId(notification.getUserId());
        dto.setType(notification.getType());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setIsRead(notification.getIsRead());
        dto.setRelatedEntityType(notification.getRelatedEntityType());
        dto.setRelatedEntityId(notification.getRelatedEntityId());
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }
}