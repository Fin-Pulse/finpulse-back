package com.example.aggregationservice.controller;

import com.example.aggregationservice.service.BankAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
public class BankAuthController {

    private final BankAuthService bankAuthService;

    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> getToken() {
        try {
            String token = bankAuthService.getTeamToken();
            return ResponseEntity.ok(Map.of(
                    "access_token", token,
                    "token_type", "bearer",
                    "source", "redis_cache"
            ));
        } catch (Exception e) {
            log.error("Error getting bank token: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get bank token"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshToken() {
        try {
            String token = bankAuthService.refreshAndCacheToken();
            return ResponseEntity.ok(Map.of(
                    "access_token", token,
                    "token_type", "bearer",
                    "message", "Token refreshed successfully"
            ));
        } catch (Exception e) {
            log.error("Error refreshing bank token: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to refresh bank token"));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getTokenStatus() {
        try {
            String token = bankAuthService.getTeamToken();
            return ResponseEntity.ok(Map.of(
                    "status", "VALID",
                    "has_token", token != null,
                    "source", "aggregation_service"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "ERROR",
                    "error", e.getMessage()
            ));
        }
    }
}