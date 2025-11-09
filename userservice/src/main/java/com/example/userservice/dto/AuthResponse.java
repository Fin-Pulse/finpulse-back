package com.example.userservice.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private String tokenType;
    private UserProfile user;
    private BankVerificationResult bankVerification;

}