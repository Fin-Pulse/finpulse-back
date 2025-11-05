package com.example.aggregationservice.service;

import com.example.aggregationservice.dto.BankVerifyResponse;
import com.example.aggregationservice.dto.PendingBank;
import com.example.aggregationservice.model.Account;
import com.example.aggregationservice.model.Bank;
import com.example.aggregationservice.model.UserConsent;
import com.example.aggregationservice.model.enums.ConsentStatus;
import com.example.aggregationservice.model.enums.VerificationStatus;
import com.example.aggregationservice.repository.AccountRepository;
import com.example.aggregationservice.repository.BankRepository;
import com.example.aggregationservice.repository.UserConsentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentStatusService {

    private final UserConsentRepository userConsentRepository;
    private final BankRepository bankRepository;
    private final BankAuthService bankAuthService;
    private final BankApiClient bankApiClient;
    private final AccountRepository accountRepository;
    private final ConsentEncryptionService encryptionService;

    /**
     * Проверяет статус pending-согласий и обновляет их при одобрении
     */
    @Transactional
    public void checkPendingConsents(String bankClientId) {
        List<UserConsent> pendingConsents = userConsentRepository.findByBankClientIdAndStatus(
                bankClientId, ConsentStatus.PENDING);

        if (pendingConsents.isEmpty()) {
            return;
        }

        String teamToken = bankAuthService.getTeamToken();

        for (UserConsent consent : pendingConsents) {
            try {
                Bank bank = bankRepository.findById(consent.getBankId())
                        .orElseThrow(() -> new RuntimeException("Bank not found"));

                // Передаем bankClientId для повторного запроса
                var statusResponse = bankApiClient.checkConsentStatus(bank, teamToken, consent.getRequestId());

                if (statusResponse.isPresent() && "approved".equals(statusResponse.get().getStatus())) {
                    // Согласие одобрено - обновляем и загружаем счета
                    var consentResponse = statusResponse.get();

                    String encryptedConsentId = encryptionService.encrypt(consentResponse.getConsentId());
                    consent.setConsentId(encryptedConsentId);
                    consent.setStatus(ConsentStatus.ACTIVE);
                    consent.setExpiresAt(consentResponse.getExpiresAt());
                    consent.setUpdatedAt(Instant.now());

                    userConsentRepository.save(consent);

                    // Загружаем счета
                    String decryptedConsentId = encryptionService.decrypt(encryptedConsentId);
                    var accounts = bankApiClient.fetchAccounts(bank, teamToken, decryptedConsentId, bankClientId);

                    for (Account account : accounts) {
                        account.setUserConsentId(consent.getId());
                        accountRepository.save(account);
                    }

                    log.info("✅ Pending consent approved for client {} in bank {}, loaded {} accounts",
                            bankClientId, bank.getCode(), accounts.size());
                } else {
                    log.info("⏳ Consent still pending for client {} in bank {}", bankClientId, bank.getCode());
                }

            } catch (Exception e) {
                log.error("Error checking consent status for client {} in bank {}: {}",
                        bankClientId, consent.getBankId(), e.getMessage());
            }
        }
    }

    /**
     * Эндпоинт для ручной проверки статуса
     */
    public BankVerifyResponse checkAndUpdateConsents(String bankClientId) {
        checkPendingConsents(bankClientId);

        // Проверяем текущее состояние после обновления
        List<Account> accounts = accountRepository.findByUserConsentIdIn(
                userConsentRepository.findByBankClientId(bankClientId).stream()
                        .map(UserConsent::getId)
                        .collect(Collectors.toList())
        );

        List<UserConsent> stillPending = userConsentRepository.findByBankClientIdAndStatus(
                bankClientId, ConsentStatus.PENDING);

        List<PendingBank> pendingBanks = stillPending.stream()
                .map(consent -> {
                    Bank bank = bankRepository.findById(consent.getBankId()).orElse(null);
                    return PendingBank.builder()
                            .bankCode(bank != null ? bank.getCode() : "unknown")
                            .bankName(bank != null ? bank.getName() : "Unknown Bank")
                            .requestId(consent.getRequestId())
                            .message("Ожидание одобрения в приложении банка")
                            .actionRequired("need_app_approval")
                            .build();
                })
                .collect(Collectors.toList());

        VerificationStatus status = !accounts.isEmpty() ?
                (pendingBanks.isEmpty() ? VerificationStatus.VERIFIED : VerificationStatus.PARTIALLY_VERIFIED) :
                (pendingBanks.isEmpty() ? VerificationStatus.NOT_FOUND : VerificationStatus.PENDING_ACTION);

        return BankVerifyResponse.builder()
                .status(status)
                .message(pendingBanks.isEmpty() ? "Все согласия обработаны" : "Есть ожидающие согласия")
                .accountsCount(accounts.size())
                .pendingBanks(pendingBanks)
                .requiresUserAction(!pendingBanks.isEmpty())
                .build();
    }
}
