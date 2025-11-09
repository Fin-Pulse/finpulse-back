package com.example.aggregationservice.service;

import com.example.aggregationservice.dto.BankVerifyResponse;
import com.example.aggregationservice.dto.ConsentResponse;
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
    private final BalanceService balanceService;
    private final NotificationService notificationService;

    @Transactional
    public void checkPendingConsents(String bankClientId) {
        List<UserConsent> pendingConsents = userConsentRepository.findByBankClientIdAndStatus(
                bankClientId, ConsentStatus.PENDING);

        if (pendingConsents.isEmpty()) {
            return;
        }


        for (UserConsent consent : pendingConsents) {
            try {
                Bank bank = bankRepository.findById(consent.getBankId())
                        .orElseThrow(() -> new RuntimeException("Bank not found"));

                var statusResponse = bankApiClient.checkConsentStatus(bank, consent.getRequestId());

                if (statusResponse.isPresent() && "approved".equals(statusResponse.get().getStatus())) {
                    var consentResponse = statusResponse.get();

                    String encryptedConsentId = encryptionService.encrypt(consentResponse.getConsentId());
                    consent.setConsentId(encryptedConsentId);
                    consent.setStatus(ConsentStatus.ACTIVE);
                    consent.setExpiresAt(consentResponse.getExpiresAt());
                    consent.setUpdatedAt(Instant.now());

                    userConsentRepository.save(consent);

                    String decryptedConsentId = encryptionService.decrypt(encryptedConsentId);
                    var accounts = bankApiClient.fetchAccounts(bank, decryptedConsentId, bankClientId);

                    for (Account account : accounts) {
                        account.setUserConsentId(consent.getId());
                        accountRepository.save(account);
                    }

                }

            } catch (Exception e) {
                log.error("Error checking consent status for client {} in bank {}: {}",
                        bankClientId, consent.getBankId(), e.getMessage());
            }
        }
    }

    public BankVerifyResponse checkAndUpdateConsents(String bankClientId) {
        checkPendingConsents(bankClientId);

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
    @Transactional
    public void processApprovedConsent(String clientId, Bank bank, ConsentResponse consentResponse) {
        try {
            UserConsent consent = userConsentRepository.findByBankClientIdAndBankIdAndStatus(
                            clientId, bank.getId(), ConsentStatus.PENDING)
                    .orElseThrow(() -> new RuntimeException("Pending consent not found"));

            String encryptedConsentId = encryptionService.encrypt(consentResponse.getConsentId());
            consent.setConsentId(encryptedConsentId);
            consent.setStatus(ConsentStatus.ACTIVE);
            consent.setExpiresAt(consentResponse.getExpiresAt());
            consent.setUpdatedAt(Instant.now());

            userConsentRepository.save(consent);

            String decryptedConsentId = encryptionService.decrypt(encryptedConsentId);
            var accounts = bankApiClient.fetchAccounts(bank,
                    decryptedConsentId, clientId);

            for (Account account : accounts) {
                account.setUserConsentId(consent.getId());
                accountRepository.save(account);
            }

            balanceService.updateBalancesForUser(clientId);
            notificationService.sendAccountsLoadedNotification(clientId, bank.getCode(), accounts.size());

        } catch (Exception e) {
            log.error("Error processing approved consent for client {} in bank {}: {}",
                    clientId, bank.getCode(), e.getMessage());
            notificationService.sendVerificationErrorNotification(clientId, bank.getCode(), e.getMessage());
            throw new RuntimeException("Failed to process approved consent", e);
        }
    }
}
