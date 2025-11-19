package com.example.notificationservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Table(name = "user_recommendations")
public class Recommendation {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String recommendations;

    @Column(nullable = false)
    private Instant createdAt;
}

