package com.example.notificationservice.controller;

import com.example.notificationservice.dto.RecommendationsDto;
import com.example.notificationservice.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WebSocketRecommendationController {

    private final RecommendationService recommendationService;

    @MessageMapping("/recommendations.subscribe")
    @SendToUser("/queue/recommendations")
    public RecommendationsDto subscribe(Principal principal) {
        if (principal == null) throw new AccessDeniedException("User not authenticated");

        UUID userId = UUID.fromString(principal.getName());

        RecommendationsDto dto = recommendationService.getLatestRecommendations(userId);

        if (dto != null) {
            log.info("Sent latest recommendations to user {}", userId);
        } else {
            log.info("No recommendations found for user {}", userId);
        }

        return dto;
    }
}
