package com.example.aggregationservice.service;

import com.example.aggregationservice.config.BankApiProperties;
import com.example.aggregationservice.dto.BankTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankAuthService {

    private final RestTemplate restTemplate;
    private final BankApiProperties bankApiProperties;

    public BankTokenResponse getBankToken() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(bankApiProperties.getBaseUrl())
                    .path(bankApiProperties.getTokenEndpoint())
                    .queryParam("client_id", bankApiProperties.getClientId())
                    .queryParam("client_secret", bankApiProperties.getClientSecret())
                    .toUriString();

            log.info("Requesting bank token from: {}", url.replace(bankApiProperties.getClientSecret(), "***"));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<BankTokenResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    BankTokenResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Successfully obtained bank token for client: {}", bankApiProperties.getClientId());
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to get bank token. Status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error getting bank token: {}", e.getMessage());
            throw new RuntimeException("Failed to get bank token from bank API", e);
        }
    }

    public boolean validateToken(String token) {
        return token != null && !token.trim().isEmpty() && token.startsWith("ey");
    }
}