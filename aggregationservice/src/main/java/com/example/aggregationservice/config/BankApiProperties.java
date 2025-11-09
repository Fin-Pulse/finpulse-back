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

    /**
     * Получает конфигурацию банка по коду (case-insensitive)
     * Поддерживает как "VBANK"/"ABANK"/"SBANK", так и "vbank"/"abank"/"sbank"
     */
    public BankConfig getBankConfig(String bankCode) {
        if (bankCode == null || banks == null) {
            return null;
        }
        
        // Сначала пробуем точное совпадение
        BankConfig config = banks.get(bankCode);
        if (config != null) {
            return config;
        }
        
        // Если не найдено, ищем case-insensitive
        return banks.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(bankCode))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}