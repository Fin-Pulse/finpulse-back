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

    @PostMapping("/{consentId}/link-client/{bankClientId}")
    public ResponseEntity<Void> linkUserToConsent(
            @PathVariable String consentId,
            @PathVariable String bankClientId) {  // меняем userId на bankClientId
        userVerificationService.linkUserToConsent(consentId, bankClientId);
        return ResponseEntity.ok().build();
    }
}