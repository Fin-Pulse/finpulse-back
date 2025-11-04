package com.example.aggregationservice.dto;

import com.example.aggregationservice.model.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankVerifyResponse {
    private VerificationStatus status;
    private String message;
    private String bank;
    private int accountsCount;
    private String consentId; // Добавим для UserService
}