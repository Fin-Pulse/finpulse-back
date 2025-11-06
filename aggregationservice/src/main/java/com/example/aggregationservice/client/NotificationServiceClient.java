package com.example.aggregationservice.client;

import com.example.aggregationservice.dto.CreateNotificationRequest;
import com.example.aggregationservice.dto.NotificationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "notification-service", url = "${notification.service.url:http://localhost:8083}")
public interface NotificationServiceClient {

    @GetMapping("/api/notifications/user/{userId}")
    List<NotificationDto> getUserNotifications(@PathVariable UUID userId);

    @GetMapping("/api/notifications/user/{userId}/unread")
    List<NotificationDto> getUnreadNotifications(@PathVariable UUID userId);

    @GetMapping("/api/notifications/user/{userId}/unread-count")
    Integer getUnreadCount(@PathVariable UUID userId);

    @PostMapping("/api/notifications")
    void createNotification(@RequestBody CreateNotificationRequest request);

    @PutMapping("/api/notifications/{notificationId}/read")
    void markAsRead(@PathVariable UUID notificationId);
}