package com.example.userservice.client;

import com.example.userservice.dto.BankVerifyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "aggregation-service", url = "${aggregation.service.url:http://localhost:8082}")
public interface AggregationServiceClient {

    @PostMapping("/api/verification/{clientId}/verify")
    BankVerifyResponse verifyClient(@PathVariable String clientId);

    @GetMapping("/api/verification/{clientId}/status")
    BankVerifyResponse checkVerificationStatus(@PathVariable String clientId);

    @PostMapping("/api/verification/{clientId}/refresh")
    BankVerifyResponse refreshAccounts(@PathVariable String clientId);
}