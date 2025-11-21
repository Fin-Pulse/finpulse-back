package com.example.productservice.dto;

import com.example.productservice.model.enums.VerificationStatus;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class LeadDto {
    private UUID id;
    private UUID userId;
    private String productId;
    private UUID bankId;

    private String userFullName;
    private String userPhone;
    private String userEmail;
    private String userBankClientId;
    private boolean userVerified;
    private VerificationStatus userVerificationStatus;

    private Object payload;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private Instant deliveredAt;
}