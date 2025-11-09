package com.example.aggregationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRequest {
    private String client_id;
    private String[] permissions;
    private String reason;
    private String requesting_bank;
    private String requesting_bank_name;
}