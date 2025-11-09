package com.example.aggregationservice.controller;

import com.example.aggregationservice.service.BankAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/bank/admin")
@RequiredArgsConstructor
public class BankAuthController {

    private final BankAuthService bankAuthService;

    @GetMapping("/{bankCode}/token")
    public ResponseEntity<Map<String, String>> getBankToken(@PathVariable String bankCode) {
        try {
            String token = bankAuthService.getBankToken(bankCode);
            return ResponseEntity.ok(Map.of(
                    "bank_code", bankCode,
                    "access_token", "***" + token.substring(token.length() - 8),
                    "token_type", "bearer",
                    "source", "redis_cache"
            ));
        } catch (Exception e) {
            log.error("Error getting bank token for {}: {}", bankCode, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", "Failed to get bank token",
                            "bank_code", bankCode
                    ));
        }
    }

    @PostMapping("/{bankCode}/refresh")
    public ResponseEntity<Map<String, String>> refreshBankToken(@PathVariable String bankCode) {
        try {
            String token = bankAuthService.refreshAndCacheToken(bankCode);
            return ResponseEntity.ok(Map.of(
                    "bank_code", bankCode,
                    "access_token", "***" + token.substring(token.length() - 8),
                    "token_type", "bearer",
                    "message", "Token refreshed successfully"
            ));
        } catch (Exception e) {
            log.error("Error refreshing bank token for {}: {}", bankCode, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", "Failed to refresh bank token",
                            "bank_code", bankCode
                    ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAllBanksTokenStatus() {
        try {
            List<String> supportedBanks = bankAuthService.getSupportedBanks();
            Map<String, Object> status = new HashMap<>();

            for (String bankCode : supportedBanks) {
                try {
                    String token = bankAuthService.getBankToken(bankCode);
                    status.put(bankCode, Map.of(
                            "status", token != null ? "VALID" : "MISSING",
                            "has_token", token != null
                    ));
                } catch (Exception e) {
                    status.put(bankCode, Map.of(
                            "status", "ERROR",
                            "error", e.getMessage()
                    ));
                }
            }

            return ResponseEntity.ok(Map.of(
                    "banks", status,
                    "total_banks", supportedBanks.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "status", "ERROR",
                            "error", e.getMessage()
                    ));
        }
    }

    @GetMapping("/{bankCode}/status")
    public ResponseEntity<Map<String, Object>> getBankTokenStatus(@PathVariable String bankCode) {
        try {
            String token = bankAuthService.getBankToken(bankCode);
            return ResponseEntity.ok(Map.of(
                    "bank_code", bankCode,
                    "status", "VALID",
                    "has_token", token != null,
                    "source", "aggregation_service"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "bank_code", bankCode,
                    "status", "ERROR",
                    "error", e.getMessage()
            ));
        }
    }
}