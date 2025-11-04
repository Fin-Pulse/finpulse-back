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

    @Transactional
    public BankVerifyResponse verifyClient(String bankClientId) {
        log.info("Starting verification for client: {}", bankClientId);

        String teamToken = bankAuthService.getTeamToken();
        List<Bank> activeBanks = bankRepository.findAllActiveBanks();

        log.info("Verifying client {} across {} active banks", bankClientId, activeBanks.size());

        for (Bank bank : activeBanks) {
            try {
                log.info("Trying bank: {} ({})", bank.getName(), bank.getCode());

                // 1. Запрашиваем согласие
                var consentOpt = bankApiClient.requestConsent(bank, teamToken, bankClientId);
                if (consentOpt.isEmpty()) {
                    log.info("Client {} not found in {}", bankClientId, bank.getCode());
                    continue;
                }

                var consentResponse = consentOpt.get();

                // 2. Сохраняем согласие (без userId)
                UserConsent consent = new UserConsent();
                consent.setBankId(bank.getId());
                consent.setConsentId(consentResponse.getConsentId());
                consent.setPermissions(String.join(",", consentResponse.getPermissions()));
                consent.setExpiresAt(consentResponse.getExpiresAt());
                consent.setStatus("active");
                consent.setUpdatedAt(Instant.now());

                UserConsent savedConsent = userConsentRepository.save(consent);

                // 3. Получаем и сохраняем счета
                var accounts = bankApiClient.fetchAccounts(bank, teamToken, consentResponse.getConsentId());

                for (Account account : accounts) {
                    account.setUserConsentId(savedConsent.getId());
                    accountRepository.save(account);
                }

                log.info("Successfully verified client {} in bank {} with {} accounts",
                        bankClientId, bank.getCode(), accounts.size());

                return BankVerifyResponse.builder()
                        .status(VerificationStatus.VERIFIED)
                        .message("Client verified and accounts loaded successfully")
                        .bank(bank.getCode())
                        .accountsCount(accounts.size())
                        .consentId(consentResponse.getConsentId())
                        .build();

            } catch (Exception e) {
                log.error("Error verifying client {} in bank {}: {}",
                        bankClientId, bank.getCode(), e.getMessage(), e);
            }
        }

        log.info("Client {} not found in any active bank", bankClientId);

        return BankVerifyResponse.builder()
                .status(VerificationStatus.NOT_FOUND)
                .message("Client not found in any active bank")
                .build();
    }

    // Метод для привязки пользователя к консенсу (вызывается из UserService)
    @Transactional
    public void linkUserToConsent(String consentId, String userId) {
        userConsentRepository.findByConsentId(consentId).ifPresent(consent -> {
            consent.setUserId(UUID.fromString(userId));
            consent.setUpdatedAt(Instant.now());
            userConsentRepository.save(consent);
            log.info("Linked user {} to consent {}", userId, consentId);
        });
    }
}