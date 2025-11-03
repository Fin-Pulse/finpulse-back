package com.example.aggregationservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bank.api")
public class BankApiProperties {
    private String baseUrl;
    private String clientId;
    private String clientSecret;
    private String tokenEndpoint = "/auth/bank-token";
    private long tokenTtlHours = 23; // На 1 час меньше чем у банка
}