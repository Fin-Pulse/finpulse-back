package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastReadyEvent {
    private String userId;
    private String bankClientId;
    private Boolean forecastReady;
    private Long timestamp;
    private String error;
}




