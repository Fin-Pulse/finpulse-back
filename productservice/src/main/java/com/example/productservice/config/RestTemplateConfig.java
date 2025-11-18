package com.example.productservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofSeconds(60))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean(name = "bankRestTemplate")
    public RestTemplate bankRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(45))
                .setReadTimeout(Duration.ofSeconds(90))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("User-Agent", "FinPulse-ProductService/1.0")
                .build();
    }
}