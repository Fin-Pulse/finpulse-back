package com.example.aggregationservice.controller;

import com.example.aggregationservice.dto.BankTokenResponse;
import com.example.aggregationservice.service.BankAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
public class BankAuthController {

    private final BankAuthService bankAuthService;

    @PostMapping("/token")
    public ResponseEntity<BankTokenResponse> getToken() {
        BankTokenResponse tokenResponse = bankAuthService.getBankToken();
        return ResponseEntity.ok(tokenResponse);
    }

    @GetMapping("/test-connection")
    public ResponseEntity<String> testConnection() {
        try {
            BankTokenResponse tokenResponse = bankAuthService.getBankToken();
            if (bankAuthService.validateToken(tokenResponse.getAccessToken())) {
                return ResponseEntity.ok("Successfully connected to bank API. Token received.");
            } else {
                return ResponseEntity.badRequest().body("Received invalid token from bank API.");
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to connect to bank API: " + e.getMessage());
        }
    }
}