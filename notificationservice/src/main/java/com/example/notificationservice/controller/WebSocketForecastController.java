package com.example.notificationservice.controller;

import com.example.notificationservice.dto.ForecastDto;
import com.example.notificationservice.service.ForecastService;
import com.example.notificationservice.service.WebSocketForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WebSocket Forecast API", description = "WebSocket API для прогнозов в реальном времени")
public class WebSocketForecastController {

    private final ForecastService forecastService;
    private final WebSocketForecastService webSocketForecastService;

    @Value("${minio.public-url:http://localhost:9000/ml-charts}")
    private String minioPublicUrl;

    @MessageMapping("/forecasts.subscribe")
    @SendToUser("/queue/forecasts")
    @Operation(summary = "Подписка на прогнозы", description = "Подписывает пользователя на получение прогнозов по WebSocket")
    public ForecastDto subscribe(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        String userIdStr = null;

        if (principal != null) {
            userIdStr = principal.getName();
        } else {
            if (headerAccessor != null) {
                Object nativeHeaders = headerAccessor.getHeader("nativeHeaders");
            }
        }

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

        ForecastDto latestForecast = forecastService.getLatestForecast(userId);
        if (latestForecast != null) {
            forecastService.enrichChartUrlsWithFullPath(latestForecast, minioPublicUrl);
            return latestForecast;
        } else {
            return null;
        }
    }
}