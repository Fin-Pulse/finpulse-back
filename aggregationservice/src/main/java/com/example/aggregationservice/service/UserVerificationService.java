package com.example.aggregationservice.service;

import com.example.aggregationservice.dto.BankVerifyResponse;
import com.example.aggregationservice.model.*;
import com.example.aggregationservice.model.enums.*;
import com.example.aggregationservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public BankVerifyResponse verifyClient(String bankClientId) {
        log.info("Starting verification for client: {}", bankClientId);

        String teamToken = bankAuthService.getTeamToken();
        List<Bank> activeBanks = bankRepository.findAllActiveBanks();

        log.info("Verifying client {} across {} active banks", bankClientId, activeBanks.size());

        List<Account> allAccounts = new ArrayList<>();
        String verifiedBank = null;
        String consentId = null;

        for (Bank bank : activeBanks) {
            try {
                log.info("Trying bank: {} ({})", bank.getName(), bank.getCode());

                // 1. Запрашиваем согласие в КАЖДОМ банке
                var consentOpt = bankApiClient.requestConsent(bank, teamToken, bankClientId);
                if (consentOpt.isEmpty()) {
                    log.info("Client {} not found in {}", bankClientId, bank.getCode());
                    continue; // пробуем следующий банк
                }

                var consentResponse = consentOpt.get();

                // 2. Сохраняем согласие для каждого банка
                UserConsent consent = new UserConsent();
                consent.setBankClientId(bankClientId);
                consent.setBankId(bank.getId());
                String encryptedConsentId = encryptionService.encrypt(consentResponse.getConsentId());
                consent.setConsentId(encryptedConsentId);
                consent.setPermissions(String.join(",", consentResponse.getPermissions()));
                consent.setExpiresAt(consentResponse.getExpiresAt());
                consent.setStatus("active");
                consent.setUpdatedAt(Instant.now());

                UserConsent savedConsent = userConsentRepository.save(consent);

                // 3. Получаем счета из этого банка
                String decryptedConsentId = encryptionService.decrypt(encryptedConsentId);
                var bankAccounts = bankApiClient.fetchAccounts(bank, teamToken, decryptedConsentId, bankClientId);
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
                // Продолжаем с другими банками даже при ошибке
            }
        }

        // 4. Формируем финальный ответ после проверки ВСЕХ банков
        if (!allAccounts.isEmpty()) {
            log.info("✅ Client {} verified with {} total accounts from multiple banks",
                    bankClientId, allAccounts.size());

            return BankVerifyResponse.builder()
                    .status(VerificationStatus.VERIFIED)
                    .message("Client verified and accounts loaded from multiple banks")
                    .bank(verifiedBank) // первый успешный банк
                    .accountsCount(allAccounts.size()) // ОБЩЕЕ количество счетов
                    .consentId(consentId)
                    .build();
        } else {
            log.info("❌ Client {} not found in any active bank", bankClientId);

            return BankVerifyResponse.builder()
                    .status(VerificationStatus.NOT_FOUND)
                    .message("Client not found in any active bank")
                    .build();
        }
    }

    // Метод для привязки пользователя к консенсу (вызывается из UserService)
    @Transactional
    public void linkUserToConsent(String consentId, String bankClientId) {
        userConsentRepository.findByConsentId(consentId).ifPresent(consent -> {
            consent.setBankClientId(bankClientId);  // просто String, не UUID
            consent.setUpdatedAt(Instant.now());
            userConsentRepository.save(consent);
            log.info("Linked bank client {} to consent {}", bankClientId, consentId);
        });
    }
    public List<Account> fetchAccountsWithDecryption(Bank bank, String teamToken, String encryptedConsentId, String clientId) {
        String decryptedConsentId = encryptionService.decrypt(encryptedConsentId);
        return bankApiClient.fetchAccounts(bank, teamToken, decryptedConsentId, clientId);
    }
}