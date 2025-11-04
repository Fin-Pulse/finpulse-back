package com.example.userservice.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    @Column(nullable = false)
    private String passwordHash;

    private String fullName;

    @Column(unique = true, nullable = false)
    private String bankClientId;

    private boolean isActive = true;
    private boolean isVerified = false;

    private String verificationToken;
    private LocalDateTime verificationTokenExpiry;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        bankClientId = "user_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}