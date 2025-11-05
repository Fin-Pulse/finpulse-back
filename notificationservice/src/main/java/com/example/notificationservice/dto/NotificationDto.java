package com.example.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Schema(description = "DTO для уведомления")
public class NotificationDto {

    @Schema(description = "UUID уведомления", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "UUID пользователя", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID userId;

    @Schema(description = "Тип уведомления", example = "WEEKLY_FORECAST_READY")
    private String type;

    @Schema(description = "Заголовок уведомления", example = "Ваш финансовый прогноз готов!")
    private String title;

    @Schema(description = "Текст уведомления", example = "Мы проанализировали ваши траты...")
    private String message;

    @Schema(description = "Флаг прочтения", example = "false")
    private Boolean isRead;

    @Schema(description = "Тип связанной сущности", example = "FORECAST")
    private String relatedEntityType;

    @Schema(description = "UUID связанной сущности", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID relatedEntityId;

    @Schema(description = "Дата и время создания уведомления")
    private LocalDateTime createdAt;
}