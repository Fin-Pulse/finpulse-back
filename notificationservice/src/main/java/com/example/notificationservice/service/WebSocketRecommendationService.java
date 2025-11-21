package com.example.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketRecommendationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendRecommendations(UUID userId, Object recommendationsDto) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/recommendations",
                    recommendationsDto
            );
            log.info("Рекомендации отправлены WS пользователю {}", userId);
        } catch (Exception e) {
            log.error("Ошибка отправки рекомендаций WS {}: {}", userId, e.getMessage());
        }
    }
}

