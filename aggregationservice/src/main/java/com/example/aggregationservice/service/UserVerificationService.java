package com.example.aggregationservice.service;

import com.example.aggregationservice.client.UserServiceClient;
import com.example.aggregationservice.dto.BankVerifyResponse;
import com.example.aggregationservice.dto.PendingBank;
import com.example.aggregationservice.dto.UserForecastUpdateEvent;
import com.example.aggregationservice.model.*;
import com.example.aggregationservice.model.enums.*;
import com.example.aggregationservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserVerificationService {

    private final BankRepository bankRepository;
    private final BankAuthService bankAuthService;
    private final BankApiClient bankApiClient;
    private final UserConsentRepository userConsentRepository;
    private final AccountRepository accountRepository;
    private final ConsentEncryptionService encryptionService;
    private final BalanceService balanceService;
    private final TransactionService transactionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserServiceClient userServiceClient;

    @Transactional
    public BankVerifyResponse verifyClient(String bankClientId) {
        log.info("Starting verification for client: {}", bankClientId);

        List<Bank> activeBanks = bankRepository.findAllActiveBanks();

        log.info("Verifying client {} across {} active banks", bankClientId, activeBanks.size());

        List<Account> allAccounts = new ArrayList<>();
        List<PendingBank> pendingBanks = new ArrayList<>();
        String verifiedBank = null;
        String consentId = null;
        boolean requiresUserAction = false;

        for (Bank bank : activeBanks) {
            try {
                log.info("Trying bank: {} ({})", bank.getName(), bank.getCode());

                var consentOpt = bankApiClient.requestConsent(bank, bankClientId);
                if (consentOpt.isEmpty()) {
                    log.info("Client {} not found in {}", bankClientId, bank.getCode());
                    continue;
                }

                var consentResponse = consentOpt.get();

                if ("pending".equals(consentResponse.getStatus())) {
                    // Сохраняем pending-согласие
                    UserConsent consent = new UserConsent();
                    consent.setBankClientId(bankClientId);
                    consent.setBankId(bank.getId());
                    consent.setRequestId(consentResponse.getRequestId());
                    consent.setStatus(ConsentStatus.PENDING);
                    consent.setPermissions(String.join(",", consentResponse.getPermissions()));
                    consent.setUpdatedAt(Instant.now());

                    userConsentRepository.save(consent);

                    // Добавляем в список pending-банков
                    PendingBank pendingBank = PendingBank.builder()
                            .bankCode(bank.getCode())
                            .bankName(bank.getName())
                            .requestId(consentResponse.getRequestId())
                            .message("Требуется одобрение в приложении банка")
                            .actionRequired("need_app_approval")
                            .build();

                    pendingBanks.add(pendingBank);
                    requiresUserAction = true;

                    log.info("Pending consent saved for bank {} with requestId: {}", bank.getCode(), consentResponse.getRequestId());
                    continue;
                }

                // Логика для approved-согласий
                String encryptedConsentId = encryptionService.encrypt(consentResponse.getConsentId());

                UserConsent consent = new UserConsent();
                consent.setBankClientId(bankClientId);
                consent.setBankId(bank.getId());
                consent.setConsentId(encryptedConsentId);
                consent.setRequestId(consentResponse.getRequestId());
                consent.setStatus(ConsentStatus.ACTIVE);
                consent.setPermissions(String.join(",", consentResponse.getPermissions()));
                consent.setExpiresAt(consentResponse.getExpiresAt());
                consent.setUpdatedAt(Instant.now());

                UserConsent savedConsent = userConsentRepository.save(consent);

                String decryptedConsentId = encryptionService.decrypt(encryptedConsentId);
                var bankAccounts = bankApiClient.fetchAccounts(bank, decryptedConsentId, bankClientId);

                for (Account account : bankAccounts) {
                    account.setUserConsentId(savedConsent.getId());
                    accountRepository.save(account);
                    allAccounts.add(account);
                }

                log.info("Found {} accounts in bank {}", bankAccounts.size(), bank.getCode());

                if (verifiedBank == null) {
                    verifiedBank = bank.getCode();
                    consentId = consentResponse.getConsentId();
                }

            } catch (Exception e) {
                log.error("Error verifying client {} in bank {}: {}",
                        bankClientId, bank.getCode(), e.getMessage());
            }
        }

        // ВЫГРУЖАЕМ БАЛАНСЫ И ТРАНЗАКЦИИ ЕСЛИ ЕСТЬ СЧЕТА
        if (!allAccounts.isEmpty()) {
            try {
                log.info("🔄 Loading balances for {} accounts of client {}", allAccounts.size(), bankClientId);
                balanceService.updateBalancesForUser(bankClientId);
                log.info("✅ Balances loaded successfully for client {}", bankClientId);

                // ВЫГРУЖАЕМ ИСТОРИЧЕСКИЕ ТРАНЗАКЦИИ (4 недели)
                log.info("🔄 Loading historical transactions for client {}", bankClientId);
                int transactionsCount = transactionService.exportHistoricalTransactions(bankClientId, 4);
                log.info("✅ Historical transactions loaded successfully for client {}: {} transactions",
                        bankClientId, transactionsCount);

                // ОТПРАВЛЯЕМ В ML ДЛЯ ПРОГНОЗА
                sendToMlService(bankClientId, "INITIAL_FORECAST");

            } catch (Exception e) {
                log.warn("⚠️ Failed to load balances/transactions for client {}: {}", bankClientId, e.getMessage());
            }
        }

        // Формируем финальный ответ
        VerificationStatus finalStatus;
        String finalMessage;

        if (!allAccounts.isEmpty()) {
            if (!pendingBanks.isEmpty()) {
                finalStatus = VerificationStatus.PARTIALLY_VERIFIED;
                finalMessage = "Часть счетов загружена, но требуются действия в других банках";
            } else {
                finalStatus = VerificationStatus.VERIFIED;
                finalMessage = "Все счета успешно загружены";
            }
        } else if (!pendingBanks.isEmpty()) {
            finalStatus = VerificationStatus.PENDING_ACTION;
            finalMessage = "Требуется одобрение в банках для загрузки счетов";
        } else {
            finalStatus = VerificationStatus.NOT_FOUND;
            finalMessage = "Клиент не найден ни в одном банке";
        }

        return BankVerifyResponse.builder()
                .status(finalStatus)
                .message(finalMessage)
                .bank(verifiedBank)
                .accountsCount(allAccounts.size())
                .consentId(consentId)
                .pendingBanks(pendingBanks)
                .requiresUserAction(requiresUserAction)
                .build();
    }

    /**
     * ОТПРАВЛЯЕМ СОБЫТИЕ В ML СЕРВИС
     */
    private void sendToMlService(String bankClientId, String analysisType) {
        try {
            // ✅ ПРАВИЛЬНО получаем userId по bankClientId через Feign client
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

                log.info("📤 Sent ML analysis task for user {} (bankClientId: {})", userId, bankClientId);

            } else {
                log.warn("⚠️ User not found for bankClientId: {}", bankClientId);
            }

        } catch (Exception e) {
            log.error("❌ Failed to send ML analysis task for bankClientId {}: {}", bankClientId, e.getMessage());
        }
    }

    // Метод для привязки пользователя к консенсу (вызывается из UserService)
    @Transactional
    public void linkUserToConsent(String consentId, String bankClientId) {
        userConsentRepository.findByConsentId(consentId).ifPresent(consent -> {
            consent.setBankClientId(bankClientId);
            consent.setUpdatedAt(Instant.now());
            userConsentRepository.save(consent);
            log.info("Linked bank client {} to consent {}", bankClientId, consentId);
        });
    }

    public List<Account> fetchAccountsWithDecryption(Bank bank, String teamToken, String encryptedConsentId, String clientId) {
        String decryptedConsentId = encryptionService.decrypt(encryptedConsentId);
        return bankApiClient.fetchAccounts(bank, decryptedConsentId, clientId);
    }
}