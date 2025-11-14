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

        List<Bank> activeBanks = bankRepository.findAllActiveBanks();

        List<Account> allAccounts = new ArrayList<>();
        List<PendingBank> pendingBanks = new ArrayList<>();
        String verifiedBank = null;
        String consentId = null;
        boolean requiresUserAction = false;

        for (Bank bank : activeBanks) {
            try {

                var consentOpt = bankApiClient.requestConsent(bank, bankClientId);
                if (consentOpt.isEmpty()) {
                    continue;
                }

                var consentResponse = consentOpt.get();

                if ("pending".equals(consentResponse.getStatus())) {
                    UserConsent consent = new UserConsent();
                    consent.setBankClientId(bankClientId);
                    consent.setBankId(bank.getId());
                    consent.setRequestId(consentResponse.getRequestId());
                    consent.setStatus(ConsentStatus.PENDING);
                    consent.setPermissions(String.join(",", consentResponse.getPermissions()));
                    consent.setUpdatedAt(Instant.now());

                    userConsentRepository.save(consent);

                    PendingBank pendingBank = PendingBank.builder()
                            .bankCode(bank.getCode())
                            .bankName(bank.getName())
                            .requestId(consentResponse.getRequestId())
                            .message("Требуется одобрение в приложении банка")
                            .actionRequired("need_app_approval")
                            .build();

                    pendingBanks.add(pendingBank);
                    requiresUserAction = true;

                    continue;
                }

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

        if (!allAccounts.isEmpty()) {
            try {
                balanceService.updateBalancesForUser(bankClientId);
                transactionService.exportHistoricalTransactions(bankClientId, 4);
                sendToMlService(bankClientId, "INITIAL_FORECAST");

            } catch (Exception e) {
                log.warn("Failed to load balances/transactions for client {}: {}", bankClientId, e.getMessage());
            }
        }

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

    @Transactional
    public void linkUserToConsent(String consentId, String bankClientId) {
        userConsentRepository.findByConsentId(consentId).ifPresent(consent -> {
            consent.setBankClientId(bankClientId);
            consent.setUpdatedAt(Instant.now());
            userConsentRepository.save(consent);
        });
    }

    public List<Account> fetchAccountsWithDecryption(Bank bank, String teamToken, String encryptedConsentId, String clientId) {
        String decryptedConsentId = encryptionService.decrypt(encryptedConsentId);
        return bankApiClient.fetchAccounts(bank, decryptedConsentId, clientId);
    }
}