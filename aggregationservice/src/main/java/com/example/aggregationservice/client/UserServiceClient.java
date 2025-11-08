package com.example.aggregationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service", url = "${user.service.url:http://localhost:8081}")
public interface UserServiceClient {

    @GetMapping("/api/bank/users/by-bank-client-id/{bankClientId}")
    ResponseEntity<UUID> getUserIdByBankClientId(@PathVariable String bankClientId);

    @GetMapping("/api/bank/users/active-ids")
    List<UUID> getAllActiveUserIds();

    @GetMapping("/api/bank/users/{userId}/bank-client-id")
    String getBankClientId(@PathVariable UUID userId);


}