package com.example.aggregationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;


@Configuration
@RequiredArgsConstructor
public class BankApiConfig {

    private final BankApiProperties bankApiProperties;

    @Bean
    public WebClient bankWebClient() {
        return WebClient.builder()
                .baseUrl(bankApiProperties.getBaseUrl())
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
    }
}