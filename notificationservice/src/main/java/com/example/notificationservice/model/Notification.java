package com.example.notificationservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Data
@Schema(description = "Модель уведомления")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Schema(description = "UUID уведомления", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    @Schema(description = "UUID пользователя", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID userId;

    @Column(name = "type", nullable = false)
    @Schema(description = "Тип уведомления", example = "WEEKLY_FORECAST_READY")
    private String type = "system";

    @Column(name = "title", nullable = false)
    @Schema(description = "Заголовок уведомления", example = "Ваш финансовый прогноз готов!")
    private String title;

    @Column(name = "message", nullable = false)
    @Schema(description = "Текст уведомления", example = "Мы проанализировали ваши траты...")
    private String message;

    @Column(name = "is_read")
    @Schema(description = "Флаг прочтения", example = "false")
    private Boolean isRead = false;

    @Column(name = "related_entity_type")
    @Schema(description = "Тип связанной сущности", example = "FORECAST")
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    @Schema(description = "UUID связанной сущности", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID relatedEntityId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Schema(description = "Дата и время создания уведомления")
    private LocalDateTime createdAt;
}