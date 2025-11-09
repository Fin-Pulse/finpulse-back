package com.example.aggregationservice.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class NotificationDto {
    private UUID id;
    private UUID userId;
    private String type;
    private String title;
    private String message;
    private boolean isRead;
    private String relatedEntityType;
    private String relatedEntityId;
    private Instant createdAt;
}