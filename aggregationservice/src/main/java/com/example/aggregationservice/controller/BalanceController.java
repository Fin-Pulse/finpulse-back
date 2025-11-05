package com.example.aggregationservice.controller;

import com.example.aggregationservice.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    @PostMapping("/{clientId}/update")
    public ResponseEntity<String> updateBalances(@PathVariable String clientId) {
        balanceService.updateBalancesForUser(clientId);
        return ResponseEntity.ok("Balances update initiated for client: " + clientId);
    }

    @PostMapping("/update-all")
    public ResponseEntity<String> updateAllBalances() {
        balanceService.updateAllBalances();
        return ResponseEntity.ok("Balance update initiated for all users");
    }
}