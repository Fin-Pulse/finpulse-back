package com.example.userservice.dto;

import com.example.userservice.entity.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankVerificationResult {
    private VerificationStatus overallStatus;
    private String message;
    private int verifiedAccountsCount;
    private List<PendingBankAction> pendingActions;
    private boolean requiresUserAction;
    private String nextSteps;
}