package com.example.aggregationservice.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CreateNotificationRequest {
    private UUID userId;
    private String type;
    private String title;
    private String message;
}