// dto/UserForecastUpdateEvent.java
package com.example.aggregationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserForecastUpdateEvent {
    private UUID userId;
    private String bankClientId;
    private String analysisType;
    private Long timestamp;
}