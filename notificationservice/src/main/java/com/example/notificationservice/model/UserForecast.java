package com.example.notificationservice.model;

import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_forecasts")
public class UserForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "forecast_amount")
    private BigDecimal forecastAmount;

    @Column(name = "confidence_min")
    private BigDecimal confidenceMin;

    @Column(name = "confidence_max")
    private BigDecimal confidenceMax;

    @Column(name = "change_percentage")
    private BigDecimal changePercentage;

    @Column(name = "last_week_amount")
    private BigDecimal lastWeekAmount;

    @Column(name = "forecast_method")
    private String forecastMethod;

    @Column(name = "forecast_week_start")
    private LocalDate forecastWeekStart;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "full_forecast_data", columnDefinition = "jsonb")
    private Map<String, Object> fullForecastData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chart_urls", columnDefinition = "jsonb")
    private Map<String, Object> chartUrls;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
