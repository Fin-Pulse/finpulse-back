package com.example.productservice.dto;


import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateLeadRequest {
    private UUID userId;
    private String productId;
    private UUID bankId;
    private Map<String, Object> payload;
}