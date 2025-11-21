package com.example.productservice.dto;

import com.example.productservice.model.enums.VerificationStatus;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserProfile {
    private UUID id;
    private String email;
    private String phone;
    private String fullName;
    private String bankClientId;
    private boolean isVerified;
    private LocalDateTime createdAt;
    private VerificationStatus verificationStatus;
}