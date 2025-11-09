package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastDto {
    private BigDecimal forecastAmount;
    private BigDecimal confidenceMin;
    private BigDecimal confidenceMax;
    private BigDecimal changePercentage;
    private BigDecimal lastWeekAmount;
    private String forecastMethod;
    private LocalDate forecastWeekStart;
    private Map<String, Object> fullForecastData;
    private Map<String, String> chartUrls;
}


