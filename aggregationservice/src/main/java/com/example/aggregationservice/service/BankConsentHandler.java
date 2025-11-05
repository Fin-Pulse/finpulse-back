package com.example.aggregationservice.service;

import com.example.aggregationservice.model.Bank;
import com.example.aggregationservice.model.ScheduledTask;
import com.example.aggregationservice.repository.BankRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;


import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankConsentHandler implements TaskHandler {

    private final BankApiClient bankApiClient;
    private final BankAuthService bankAuthService;
    private final ConsentStatusService consentStatusService;
    private final BankRepository bankRepository;
    private final ObjectMapper objectMapper;
    private final TaskSchedulerService taskSchedulerService;

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

                // Обновляем согласие и загружаем счета
                consentStatusService.processApprovedConsent(clientId, bank, statusResponse.get());

                // 🎯 Тут можно отправить уведомление через NotificationService
                // notificationService.sendConsentApproved(clientId, bankCode);

                // Задача выполнена - удаляем
                // taskSchedulerService.deleteTask(task.getId());

                log.info("🗑️ Deleted monitoring task for bank {} after successful consent", bankCode);

            } else if (currentCheck < maxChecks - 1) {
                // ⏳ Еще не approved - планируем следующую проверку
                scheduleNextCheck(clientId, bankCode, requestId, maxChecks, currentCheck + 1);
            } else {
                // ❌ Превышен лимит проверок
                log.warn("❌ Bank consent monitoring timeout for client {} in bank {} after {} checks",
                        clientId, bankCode, maxChecks);
                // Можно отправить уведомление пользователю
                // notificationService.sendConsentTimeout(clientId, bankCode);
            }

        } catch (Exception e) {
            log.error("Bank consent monitoring failed for task {}: {}", task.getTaskName(), e.getMessage());
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
