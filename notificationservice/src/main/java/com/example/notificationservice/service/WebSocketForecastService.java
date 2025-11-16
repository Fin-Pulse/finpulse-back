package com.example.notificationservice.service;

import com.example.notificationservice.dto.ForecastDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketForecastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ForecastService forecastService;

    @Value("${minio.public-url:http://localhost:9000/ml-charts}")
    private String minioPublicUrl;

    public void sendForecastToUser(UUID userId, ForecastDto forecast) {
        if (forecast == null) {
            return;
        }

        try {
            forecastService.enrichChartUrlsWithFullPath(forecast, minioPublicUrl);

            String destination = "/queue/forecasts";

            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    destination,
                    forecast
            );

        } catch (Exception e) {
            log.error("Ошибка отправки прогноза пользователю {}: {}", userId, e.getMessage(), e);
        }
    }
}




