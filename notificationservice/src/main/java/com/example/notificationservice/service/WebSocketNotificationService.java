package com.example.notificationservice.service;

import com.example.notificationservice.dto.NotificationDto;
import com.example.notificationservice.exception.NotificationAccessDeniedException;
import com.example.notificationservice.exception.NotificationNotFoundException;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;

    public void sendNotificationToUser(UUID userId, NotificationDto notification) {
        String destination = "/queue/notifications";
        log.info("Sending notification to user {}: {}", userId, notification);
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                destination,
                notification
        );
    }

    public void broadcastNotification(NotificationDto notification) {
        String destination = "/topic/notifications";
        log.info("Broadcasting notification: {}", notification);
        messagingTemplate.convertAndSend(destination, notification);
    }

    @Transactional
    public void markAsReadAndNotify(UUID notificationId, UUID userId) {
        try {
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new NotificationNotFoundException(notificationId));

            // Проверяем, что уведомление принадлежит пользователю
            if (!notification.getUserId().equals(userId)) {
                throw new NotificationAccessDeniedException(notificationId, userId);
            }

            notification.setIsRead(true);
            Notification updatedNotification = notificationRepository.save(notification);

            // Отправляем обновленное уведомление обратно пользователю
            NotificationDto dto = convertToDto(updatedNotification);
            sendNotificationToUser(userId, dto);

            log.info("Notification {} marked as read for user {}", notificationId, userId);
        } catch (Exception e) {
            log.error("Error marking notification as read: {}", e.getMessage());
            // Отправляем сообщение об ошибке пользователю
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/errors",
                    "Failed to mark notification as read: " + e.getMessage()
            );
        }
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