package com.example.notificationservice.service;

import com.example.notificationservice.dto.CreateNotificationRequest;
import com.example.notificationservice.dto.ForecastDto;
import com.example.notificationservice.dto.ForecastReadyEvent;
import com.example.notificationservice.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastReadyConsumer {

    private final NotificationService notificationService;
    private final WebSocketForecastService webSocketForecastService;
    private final ForecastService forecastService;
    private final NotificationCoordinator notificationCoordinator;

    @KafkaListener(topics = "forecast_ready", groupId = "notification-service")
    @Transactional
    public void consumeForecastReadyEvent(ForecastReadyEvent event) {
        try {

            UUID userId = UUID.fromString(event.getUserId());

            if (Boolean.TRUE.equals(event.getForecastReady())) {

                ForecastDto forecast = forecastService.getLatestForecast(userId);

                if (forecast != null) {
                    webSocketForecastService.sendForecastToUser(userId, forecast);
                }

                CreateNotificationRequest notificationRequest = new CreateNotificationRequest();
                notificationRequest.setUserId(userId);
                notificationRequest.setType("FORECAST_READY");
                notificationRequest.setTitle("📊 Ваш финансовый прогноз готов!");
                notificationRequest.setMessage("Мы проанализировали ваши траты и подготовили прогноз на следующую неделю. Откройте раздел прогнозов, чтобы увидеть детали.");
                notificationRequest.setRelatedEntityType("FORECAST");

                Notification notification = notificationCoordinator.createNotificationWithWebSocket(notificationRequest);

            } else {

                CreateNotificationRequest notificationRequest = new CreateNotificationRequest();
                notificationRequest.setUserId(userId);
                notificationRequest.setType("FORECAST_ERROR");
                notificationRequest.setTitle("⚠️ Ошибка создания прогноза");
                notificationRequest.setMessage("Не удалось создать финансовый прогноз. Попробуйте позже или обратитесь в поддержку.");
                notificationRequest.setRelatedEntityType("FORECAST");

                notificationService.createNotification(notificationRequest);
            }

        } catch (Exception e) {
            log.error("Ошибка обработки события forecast_ready: {}", e.getMessage(), e);
        }
    }
}


