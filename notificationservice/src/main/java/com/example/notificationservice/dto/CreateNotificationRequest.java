package com.example.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Запрос на создание уведомления")
public class CreateNotificationRequest {

    @Schema(description = "UUID пользователя", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
    private UUID userId;

    @Schema(description = "Тип уведомления",
            example = "WEEKLY_FORECAST_READY",
            allowableValues = {"WEEKLY_FORECAST_READY", "BUDGET_ALERT", "SUBSCRIPTION_REMINDER", "WELCOME", "system"})
    private String type = "system";

    @Schema(description = "Заголовок уведомления", example = "Ваш финансовый прогноз готов!", required = true)
    private String title;

    @Schema(description = "Текст уведомления",
            example = "Мы проанализировали ваши траты и готовы показать прогноз на следующую неделю",
            required = true)
    private String message;

    @Schema(description = "Тип связанной сущности", example = "FORECAST")
    private String relatedEntityType;

    @Schema(description = "UUID связанной сущности", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID relatedEntityId;
}