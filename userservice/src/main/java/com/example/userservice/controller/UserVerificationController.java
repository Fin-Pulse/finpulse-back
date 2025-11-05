package com.example.userservice.controller;

import com.example.userservice.client.AggregationServiceClient;
import com.example.userservice.dto.BankVerifyResponse;
import com.example.userservice.entity.User;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/bank/users")
@RequiredArgsConstructor
public class UserVerificationController {

    private final UserRepository userRepository;
    private final AggregationServiceClient aggregationServiceClient;

    @PostMapping("/{userId}/verify-banks")
    public ResponseEntity<BankVerifyResponse> verifyUserBanks(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        BankVerifyResponse response = aggregationServiceClient.verifyClient(user.getBankClientId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/verification-status")
    public ResponseEntity<BankVerifyResponse> getVerificationStatus(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        BankVerifyResponse response = aggregationServiceClient.checkVerificationStatus(user.getBankClientId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/refresh-accounts")
    public ResponseEntity<BankVerifyResponse> refreshUserAccounts(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        BankVerifyResponse response = aggregationServiceClient.refreshAccounts(user.getBankClientId());
        return ResponseEntity.ok(response);
    }
}