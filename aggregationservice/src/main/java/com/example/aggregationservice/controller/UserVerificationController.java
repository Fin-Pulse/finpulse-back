package com.example.aggregationservice.controller;

import com.example.aggregationservice.dto.BankVerifyResponse;
import com.example.aggregationservice.dto.PendingBank;
import com.example.aggregationservice.service.ConsentStatusService;
import com.example.aggregationservice.service.TaskSchedulerService;
import com.example.aggregationservice.service.UserVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class UserVerificationController {

    private final UserVerificationService userVerificationService;
    private final ConsentStatusService consentStatusService;
    private final TaskSchedulerService taskSchedulerService;

    @PostMapping("/{clientId}/verify")
    public ResponseEntity<BankVerifyResponse> verifyClient(@PathVariable String clientId) {
        BankVerifyResponse response = userVerificationService.verifyClient(clientId);

        if (response.isRequiresUserAction() && response.getPendingBanks() != null) {
            scheduleBankMonitoringTasks(clientId, response.getPendingBanks());
        }

        return ResponseEntity.ok(response);
    }

    private void scheduleBankMonitoringTasks(String clientId, List<PendingBank> pendingBanks) {
        for (PendingBank pendingBank : pendingBanks) {
            // Создаем задачу для проверки каждые 2 минуты в течение 24 часов
            Map<String, Object> taskData = Map.of(
                    "clientId", clientId,
                    "bankCode", pendingBank.getBankCode(),
                    "requestId", pendingBank.getRequestId(),
                    "maxChecks", 720 // 24 часа * 60 минут / 2 минуты
            );

            Instant firstCheck = Instant.now().plusSeconds(120); // Первая проверка через 2 минуты

            taskSchedulerService.scheduleTask(
                    "BANK_CONSENT_MONITORING",
                    String.format("MONITOR_%s_%s", clientId, pendingBank.getBankCode()),
                    taskData,
                    firstCheck
            );

            log.info("📅 Scheduled bank monitoring: client={}, bank={}",
                    clientId, pendingBank.getBankCode());
        }
    }

    @PostMapping("/{consentId}/link-client/{bankClientId}")
    public ResponseEntity<Void> linkUserToConsent(
            @PathVariable String consentId,
            @PathVariable String bankClientId) {  // меняем userId на bankClientId
        userVerificationService.linkUserToConsent(consentId, bankClientId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{clientId}/status")
    public ResponseEntity<BankVerifyResponse> checkConsentStatus(@PathVariable String clientId) {
        BankVerifyResponse response = consentStatusService.checkAndUpdateConsents(clientId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{clientId}/refresh")
    public ResponseEntity<BankVerifyResponse> refreshAccounts(@PathVariable String clientId) {
        // Проверяем статус и обновляем счета
        consentStatusService.checkPendingConsents(clientId);
        BankVerifyResponse response = consentStatusService.checkAndUpdateConsents(clientId);
        return ResponseEntity.ok(response);
    }
}