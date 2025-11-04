package com.example.aggregationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentResponse {
    private String consentId;
    private String[] permissions;
    private java.time.Instant expiresAt;
    private java.time.Instant createdAt;
    private String status;
    private Boolean autoApproved;
}
