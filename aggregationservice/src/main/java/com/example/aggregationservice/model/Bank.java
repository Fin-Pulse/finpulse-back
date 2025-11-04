package com.example.aggregationservice.model;

import lombok.Data;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "banks")
public class Bank {
    @Id
    @GeneratedValue
    private UUID id;

    private String name;
    private String code;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}