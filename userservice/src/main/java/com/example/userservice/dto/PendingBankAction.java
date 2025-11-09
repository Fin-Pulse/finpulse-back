package com.example.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingBankAction {
    private String bankCode;
    private String bankName;
    private String requestId;
    private String actionMessage;
    private String actionType;
    private String deepLink;
    private String instructions;
}