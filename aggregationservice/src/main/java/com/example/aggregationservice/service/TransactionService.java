package com.example.aggregationservice.service;

import com.example.aggregationservice.client.UserServiceClient;
import com.example.aggregationservice.model.Account;
import com.example.aggregationservice.model.Bank;
import com.example.aggregationservice.model.Transaction;
import com.example.aggregationservice.model.UserConsent;
import com.example.aggregationservice.model.enums.ConsentStatus;
import com.example.aggregationservice.repository.AccountRepository;
import com.example.aggregationservice.repository.BankRepository;
import com.example.aggregationservice.repository.TransactionRepository;
import com.example.aggregationservice.repository.UserConsentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final BankApiClient bankApiClient;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserConsentRepository userConsentRepository;
    private final BankAuthService bankAuthService;
    private final ConsentEncryptionService encryptionService;
    private final BankRepository bankRepository;
    private final UserServiceClient userServiceClient;

    /**
     * Выгружает исторические транзакции за указанное количество недель
     */
    @Transactional
    public int exportHistoricalTransactions(String bankClientId, int weeks) {
        LocalDateTime toDate = LocalDateTime.now();
        LocalDateTime fromDate = toDate.minusWeeks(weeks);
        return exportTransactionsForPeriod(bankClientId, fromDate, toDate);
    }

    /**
     * Выгружает транзакции за указанный период
     */
    @Transactional
    public int exportTransactionsForPeriod(String bankClientId, LocalDateTime fromDate, LocalDateTime toDate) {
        log.info("📥 Exporting transactions for: {} from {} to {}", bankClientId, fromDate, toDate);

        List<Account> accounts = accountRepository.findActiveAccountsByBankClientId(bankClientId);
        List<UserConsent> activeConsents = userConsentRepository.findByBankClientIdAndStatus(
                bankClientId, ConsentStatus.ACTIVE);

        if (accounts.isEmpty() || activeConsents.isEmpty()) {
            log.info("No active accounts/consents for: {}", bankClientId);
            return 0;
        }

        int totalTransactions = 0;

        for (Account account : accounts) {
            UserConsent consent = findConsentForAccount(account, activeConsents);
            if (consent == null) continue;

            Bank bank = bankRepository.findById(consent.getBankId()).orElse(null);
            if (bank == null) continue;

            try {
                String decryptedConsentId = encryptionService.decrypt(consent.getConsentId());
                List<Transaction> transactions = bankApiClient.fetchAccountTransactions(
                        bank, decryptedConsentId,
                        account.getExternalAccountId(), fromDate, toDate
                );

                int savedCount = saveTransactions(account.getId(), bankClientId, transactions);
                totalTransactions += savedCount;

                log.debug("Account {}: saved {}/{} transactions",
                        account.getId(), savedCount, transactions.size());

            } catch (Exception e) {
                log.error("Failed to export transactions for account {}: {}", account.getId(), e.getMessage());
            }
        }

        log.info("✅ Exported {} transactions for {}", totalTransactions, bankClientId);
        return totalTransactions;
    }

    /**
     * Находит согласие для счета
     */
    private UserConsent findConsentForAccount(Account account, List<UserConsent> consents) {
        return consents.stream()
                .filter(c -> c.getId().equals(account.getUserConsentId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Сохраняет транзакции с проверкой на дубликаты
     */
    private int saveTransactions(UUID accountId, String bankClientId, List<Transaction> transactions) {
        int savedCount = 0;

        // Получаем user_id один раз для всех транзакций
        UUID userId = getUserIdByBankClientId(bankClientId);
        if (userId == null) {
            log.warn("⚠️ Cannot save transactions: user not found for bankClientId: {}", bankClientId);
            return 0;
        }

        for (Transaction transaction : transactions) {
            try {
                // Проверяем, не существует ли уже такая транзакция
                if (!transactionRepository.existsByAccountIdAndExternalTransactionId(
                        accountId, transaction.getExternalTransactionId())) {

                    transaction.setAccountId(accountId);
                    transaction.setUserId(userId);
                    transaction.setBankClientId(bankClientId);
                    
                    // Убеждаемся, что absoluteAmount установлен
                    if (transaction.getAbsoluteAmount() == null || transaction.getAbsoluteAmount().signum() == 0) {
                        transaction.setAbsoluteAmount(transaction.getAmount().abs());
                    }
                    
                    // Убеждаемся, что isExpense установлен
                    if (transaction.getIsExpense() == null) {
                        transaction.setIsExpense("Debit".equalsIgnoreCase(transaction.getCreditDebitIndicator()));
                    }
                    
                    transactionRepository.save(transaction);
                    savedCount++;
                }
            } catch (Exception e) {
                log.warn("⚠️ Failed to save transaction {} for account {}",
                        transaction.getExternalTransactionId(), accountId, e);
            }
        }

        return savedCount;
    }

    /**
     * Получает user_id по bank_client_id через UserServiceClient
     */
    private UUID getUserIdByBankClientId(String bankClientId) {
        try {
            ResponseEntity<UUID> response = userServiceClient.getUserIdByBankClientId(bankClientId);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.warn("⚠️ User not found for bankClientId: {}", bankClientId);
                return null;
            }
        } catch (Exception e) {
            log.error("❌ Failed to get userId for bankClientId {}: {}", bankClientId, e.getMessage());
            return null;
        }
    }
}