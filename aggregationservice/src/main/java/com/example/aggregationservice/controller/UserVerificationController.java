package com.example.aggregationservice.controller;

import com.example.aggregationservice.dto.BankVerifyResponse;
import com.example.aggregationservice.service.UserVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class UserVerificationController {

    private final UserVerificationService userVerificationService;

    @PostMapping("/{clientId}/verify")
    public ResponseEntity<BankVerifyResponse> verifyClient(@PathVariable String clientId) {
        BankVerifyResponse response = userVerificationService.verifyClient(clientId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{consentId}/link-user/{userId}")
    public ResponseEntity<Void> linkUserToConsent(
            @PathVariable String consentId,
            @PathVariable String userId) {
        userVerificationService.linkUserToConsent(consentId, userId);
        return ResponseEntity.ok().build();
    }
}