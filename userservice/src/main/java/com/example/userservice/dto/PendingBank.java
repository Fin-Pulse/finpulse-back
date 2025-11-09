package com.example.userservice.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingBank {
    private String bankCode;
    private String bankName;
    private String requestId;
    private String message;
    private String actionRequired;
}