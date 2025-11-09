package com.example.aggregationservice.service;

import com.example.aggregationservice.client.UserServiceClient;
import com.example.aggregationservice.dto.UserForecastUpdateEvent;
import com.example.aggregationservice.model.Account;
import com.example.aggregationservice.model.Bank;
import com.example.aggregationservice.model.ScheduledTask;
import com.example.aggregationservice.repository.AccountRepository;
import com.example.aggregationservice.repository.BankRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankConsentHandler implements TaskHandler {

    private final BankApiClient bankApiClient;
    private final ConsentStatusService consentStatusService;
    private final BankRepository bankRepository;
    private final AccountRepository accountRepository;
    private final TaskSchedulerService taskSchedulerService;
    private final BalanceService balanceService;
    private final TransactionService transactionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserServiceClient userServiceClient;

    @Override
    public String getSupportedTaskType() {
        return "BANK_CONSENT_MONITORING";
    }

    @Override
    public void handle(ScheduledTask task) {
        try {
            Map<String, Object> taskData = task.getTaskData();

            String clientId = (String) taskData.get("clientId");
            String bankCode = (String) taskData.get("bankCode");
            String requestId = (String) taskData.get("requestId");
            int maxChecks = (Integer) taskData.get("maxChecks");
            int currentCheck = taskData.containsKey("currentCheck") ?
                    (Integer) taskData.get("currentCheck") : 0;



            Bank bank = bankRepository.findByCode(bankCode)
                    .orElseThrow(() -> new RuntimeException(
                            String.format("Bank with code '%s' not found in database", bankCode)
                    ));

            var statusResponse = bankApiClient.checkConsentStatus(bank, requestId);

            if (statusResponse.isPresent() && "approved".equals(statusResponse.get().getStatus())) {

                consentStatusService.processApprovedConsent(clientId, bank, statusResponse.get());

                List<Account> activeAccounts = accountRepository.findActiveAccountsByBankClientId(clientId);

                if (!activeAccounts.isEmpty()) {
                    try {
                        balanceService.updateBalancesForUser(clientId);
                        int transactionsCount = transactionService.exportHistoricalTransactions(clientId, 4);
                        sendToMlService(clientId, "CONSENT_APPROVED_FORECAST");

                    } catch (Exception e) {
                        log.warn("Failed to load balances/transactions for client {}: {}", clientId, e.getMessage());
                    }
                }


            } else if (currentCheck < maxChecks - 1) {
                scheduleNextCheck(clientId, bankCode, requestId, maxChecks, currentCheck + 1);
            } else {
                log.warn("Bank consent monitoring timeout for client {} in bank {} after {} checks",
                        clientId, bankCode, maxChecks);
            }

        } catch (Exception e) {
            log.error("Bank consent monitoring failed for task {}: {}", task.getTaskName(), e.getMessage());
        }
    }

    private void sendToMlService(String bankClientId, String analysisType) {
        try {
            ResponseEntity<UUID> response = userServiceClient.getUserIdByBankClientId(bankClientId);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UUID userId = response.getBody();

                UserForecastUpdateEvent event = UserForecastUpdateEvent.builder()
                        .userId(userId)
                        .bankClientId(bankClientId)
                        .analysisType(analysisType)
                        .timestamp(System.currentTimeMillis())
                        .build();

                kafkaTemplate.send("user_forecast_update", userId.toString(), event);

            }

        } catch (Exception e) {
            log.error("Failed to send ML analysis task for bankClientId {}: {}", bankClientId, e.getMessage());
        }
    }

    private void scheduleNextCheck(String clientId, String bankCode, String requestId,
                                   int maxChecks, int nextCheck) {
        Map<String, Object> nextTaskData = Map.of(
                "clientId", clientId,
                "bankCode", bankCode,
                "requestId", requestId,
                "maxChecks", maxChecks,
                "currentCheck", nextCheck
        );

        Instant nextExecution = Instant.now().plusSeconds(120); // Через 2 минуты

        taskSchedulerService.scheduleTask(
                "BANK_CONSENT_MONITORING",
                String.format("MONITOR_%s_%s_%d", clientId, bankCode, nextCheck),
                nextTaskData,
                nextExecution
        );

    }

    @Override
    public boolean shouldDeleteAfterSuccess() {
        return true;
    }
}