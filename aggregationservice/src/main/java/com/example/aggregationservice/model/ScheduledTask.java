package com.example.aggregationservice.model;

import lombok.Data;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "scheduled_tasks")
public class ScheduledTask {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "task_name", nullable = false)
    private String taskName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> taskData;

    @Column(name = "scheduled_time", nullable = false)
    private Instant scheduledTime;

    private String status = "PENDING";
    private Integer priority = 5;
    private Integer maxRetries = 3;
    private Integer retryCount = 0;
    private String lastError;
    private String lockedBy;
    private Instant lockedAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}