package com.example.notificationservice.controller;

import com.example.notificationservice.dto.ForecastDto;
import com.example.notificationservice.service.ForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "WebSocket Forecast API", description = "WebSocket API для прогнозов в реальном времени")
public class WebSocketForecastController {

    private final ForecastService forecastService;

    @Value("${minio.public-url:http://localhost:9000/ml-charts}")
    private String minioPublicUrl;

    @MessageMapping("/forecasts.subscribe")
    @SendToUser("/queue/forecasts")
    @Operation(summary = "Подписка на прогнозы", description = "Подписывает пользователя на получение прогнозов по WebSocket")
    public ForecastDto subscribe(Principal principal) {
        if (principal == null) {
            log.error("Forecast subscription attempt without authentication");
            throw new AccessDeniedException("User not authenticated");
        }

        String userIdStr = principal.getName();

        if (userIdStr == null || userIdStr.isEmpty()) {
            log.error("Cannot determine user ID for forecast subscription");
            return null;
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid userId format: {}", userIdStr);
            return null;
        }

        try {
            ForecastDto latestForecast = forecastService.getLatestForecast(userId);
            if (latestForecast != null) {
                forecastService.enrichChartUrlsWithFullPath(latestForecast, minioPublicUrl);
                log.info("Sent latest forecast to user: {}", userId);
                return latestForecast;
            } else {
                log.info("No forecast found for user: {}", userId);
                return null;
            }
        } catch (Exception e) {
            log.error("Error getting forecast for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
}