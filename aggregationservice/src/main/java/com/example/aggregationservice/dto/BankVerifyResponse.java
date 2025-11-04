package com.example.aggregationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankVerifyResponse {
    private String status;
    private String message;
    private String bank;
    private int accountsCount;
}