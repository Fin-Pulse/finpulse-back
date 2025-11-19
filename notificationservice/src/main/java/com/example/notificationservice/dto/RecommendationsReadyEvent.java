package com.example.notificationservice.dto;

import lombok.Data;

@Data
public class RecommendationsReadyEvent {
    private String userId;
    private Boolean recommendationsReady;
    private Long timestamp;
}
