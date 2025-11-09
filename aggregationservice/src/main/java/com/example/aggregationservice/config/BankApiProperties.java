package com.example.aggregationservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "bank.api")
public class BankApiProperties {

    @Data
    public static class BankConfig {
        private String baseUrl;
        private String clientId;
        private String clientSecret;
        private String tokenEndpoint;
        private long tokenTtlHours = 24;
    }

    private Map<String, BankConfig> banks;

    public BankConfig getBankConfig(String bankCode) {
        if (bankCode == null || banks == null) {
            return null;
        }
        
        BankConfig config = banks.get(bankCode);
        if (config != null) {
            return config;
        }
        
        return banks.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(bankCode))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}