package com.example.aggregationservice.service;

import com.example.aggregationservice.config.BankApiProperties;
import com.example.aggregationservice.dto.BankTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankAuthService {

    private final WebClient bankWebClient;
    private final BankApiProperties bankApiProperties;

    public BankTokenResponse getBankToken() {
        try {
            String url = UriComponentsBuilder.fromPath(bankApiProperties.getTokenEndpoint())
                    .queryParam("client_id", bankApiProperties.getClientId())
                    .queryParam("client_secret", bankApiProperties.getClientSecret())
                    .toUriString();

            return bankWebClient.post()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(BankTokenResponse.class)
                    .block();

        } catch (Exception e) {
            log.error("Error getting bank token: {}", e.getMessage());
            throw new RuntimeException("Failed to get bank token", e);
        }
    }

    public boolean validateToken(String token) {
        // Basic token validation
        return token != null && !token.trim().isEmpty() && token.startsWith("ey");
    }
}