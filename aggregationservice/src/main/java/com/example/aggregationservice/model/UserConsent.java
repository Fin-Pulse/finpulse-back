package com.example.aggregationservice.model;

import lombok.Data;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "user_consents")
public class UserConsent {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "bank_id")
    private UUID bankId;

    @Column(name = "consent_id")
    private String consentId;

    private String status = "active";
    private String permissions;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}