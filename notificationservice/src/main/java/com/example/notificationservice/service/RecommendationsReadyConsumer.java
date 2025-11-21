package com.example.notificationservice.service;


import com.example.notificationservice.dto.CreateNotificationRequest;
import com.example.notificationservice.dto.RecommendationsReadyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationsReadyConsumer {

    private final NotificationService notificationService;
    private final NotificationCoordinator notificationCoordinator;
    private final WebSocketRecommendationService webSocketRecommendationService;
    private final RecommendationService recommendationService;

    @KafkaListener(
            topics = "recommendations_ready",
            groupId = "notification-service",
            containerFactory = "recommendationsKafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(RecommendationsReadyEvent event) {

        try {
            UUID userId = UUID.fromString(event.getUserId());

            if (Boolean.TRUE.equals(event.getRecommendationsReady())) {

                // 1. Читаем рекомендации из БД
                var recommendations = recommendationService.getLatestRecommendations(userId);

                // 2. Отправляем по WebSocket
                if (recommendations != null) {
                    webSocketRecommendationService.sendRecommendations(userId, recommendations);
                }

                // 3. Создаём уведомление + пушим по WebSocket
                CreateNotificationRequest notificationRequest = new CreateNotificationRequest();
                notificationRequest.setUserId(userId);
                notificationRequest.setType("RECOMMENDATIONS_READY");
                notificationRequest.setTitle("🎯 Новые финансовые рекомендации");
                notificationRequest.setMessage("Мы обновили список предложений, подходящих вашему профилю.");
                notificationRequest.setRelatedEntityType("RECOMMENDATION");

                notificationCoordinator.createNotificationWithWebSocket(notificationRequest);

            } else {

                CreateNotificationRequest notificationRequest = new CreateNotificationRequest();
                notificationRequest.setUserId(userId);
                notificationRequest.setType("RECOMMENDATIONS_ERROR");
                notificationRequest.setTitle("⚠️ Ошибка формирования рекомендаций");
                notificationRequest.setMessage("Не удалось создать рекомендации. Попробуйте позже.");
                notificationRequest.setRelatedEntityType("RECOMMENDATION");

                notificationService.createNotification(notificationRequest);
            }

        } catch (Exception e) {
            log.error("Ошибка обработки recommendations_ready: {}", e.getMessage(), e);
        }
    }
}
