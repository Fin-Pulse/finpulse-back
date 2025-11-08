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
    private final BankAuthService bankAuthService;
    private final ConsentStatusService consentStatusService;
    private final BankRepository bankRepository;
    private final AccountRepository accountRepository; // ✅ ДОБАВЛЯЕМ
    private final ObjectMapper objectMapper;
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

            log.info("🔍 Checking bank consent: client={}, bank={}, check={}/{}",
                    clientId, bankCode, currentCheck + 1, maxChecks);

            Bank bank = bankRepository.findByCode(bankCode)
                    .orElseThrow(() -> new RuntimeException(
                            String.format("Bank with code '%s' not found in database", bankCode)
                    ));
            String teamToken = bankAuthService.getTeamToken();

            // Проверяем статус согласия в банке
            var statusResponse = bankApiClient.checkConsentStatus(bank, teamToken, requestId);

            if (statusResponse.isPresent() && "approved".equals(statusResponse.get().getStatus())) {
                // ✅ Согласие approved - загружаем счета
                log.info("✅ Bank consent approved for client {} in bank {}", clientId, bankCode);

                // ✅ ОБНОВЛЯЕМ СОГЛАСИЕ (void метод)
                consentStatusService.processApprovedConsent(clientId, bank, statusResponse.get());

                // ✅ ПОЛУЧАЕМ АКТИВНЫЕ СЧЕТА ИЗ БАЗЫ
                List<Account> activeAccounts = accountRepository.findActiveAccountsByBankClientId(clientId);

                // ✅ ВЫГРУЖАЕМ БАЛАНСЫ И ТРАНЗАКЦИИ ЕСЛИ ЕСТЬ СЧЕТА
                if (!activeAccounts.isEmpty()) {
                    try {
                        log.info("🔄 Loading balances for {} accounts of client {}", activeAccounts.size(), clientId);
                        balanceService.updateBalancesForUser(clientId);
                        log.info("✅ Balances loaded successfully for client {}", clientId);

                        // ✅ ВЫГРУЖАЕМ ИСТОРИЧЕСКИЕ ТРАНЗАКЦИИ (4 недели)
                        log.info("🔄 Loading historical transactions for client {}", clientId);
                        int transactionsCount = transactionService.exportHistoricalTransactions(clientId, 4);
                        log.info("✅ Historical transactions loaded successfully for client {}: {} transactions",
                                clientId, transactionsCount);

                        // ✅ ОТПРАВЛЯЕМ В ML ДЛЯ ПРОГНОЗА
                        sendToMlService(clientId, "CONSENT_APPROVED_FORECAST");

                    } catch (Exception e) {
                        log.warn("⚠️ Failed to load balances/transactions for client {}: {}", clientId, e.getMessage());
                    }
                }

                log.info("🎉 Completed full processing for approved consent: client={}, bank={}", clientId, bankCode);

            } else if (currentCheck < maxChecks - 1) {
                // ⏳ Еще не approved - планируем следующую проверку
                scheduleNextCheck(clientId, bankCode, requestId, maxChecks, currentCheck + 1);
            } else {
                // ❌ Превышен лимит проверок
                log.warn("❌ Bank consent monitoring timeout for client {} in bank {} after {} checks",
                        clientId, bankCode, maxChecks);
            }

        } catch (Exception e) {
            log.error("Bank consent monitoring failed for task {}: {}", task.getTaskName(), e.getMessage());
        }
    }

    /**
     * ✅ ОТПРАВЛЯЕМ СОБЫТИЕ В ML СЕРВИС
     */
    private void sendToMlService(String bankClientId, String analysisType) {
        try {
            // Получаем userId по bankClientId через Feign client
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

                log.info("📤 Sent ML analysis task after consent approval for user {} (bankClientId: {})",
                        userId, bankClientId);

            } else {
                log.warn("⚠️ User not found for bankClientId: {}", bankClientId);
            }

        } catch (Exception e) {
            log.error("❌ Failed to send ML analysis task for bankClientId {}: {}", bankClientId, e.getMessage());
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

        log.debug("Scheduled next check for client {} bank {} (check {})",
                clientId, bankCode, nextCheck + 1);
    }

    @Override
    public boolean shouldDeleteAfterSuccess() {
        return true; // 🔥 УДАЛЯЕМ ПОСЛЕ УСПЕХА
    }
}