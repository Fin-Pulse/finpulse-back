package com.example.aggregationservice.service;

import com.example.aggregationservice.model.ScheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceUpdateHandler implements TaskHandler {

    private final BalanceService balanceService;

    @Override
    public String getSupportedTaskType() {
        return "BALANCE_UPDATE";
    }

    @Override
    public void handle(ScheduledTask task) {
        try {
            Map<String, Object> taskData = task.getTaskData();

            String clientId = (String) taskData.get("clientId");
            if (clientId != null) {
                log.info("Updating balances for user: {}", clientId);
                balanceService.updateBalancesForUser(clientId);
            } else {
                log.info("Updating balances for all users");
                balanceService.updateAllBalances();
            }
        } catch (Exception e) {
            log.error("Failed to process balance update task", e);
            throw new RuntimeException("Balance update task failed", e);
        }
    }
    @Override
    public boolean shouldDeleteAfterSuccess() {
        return false;
    }
}