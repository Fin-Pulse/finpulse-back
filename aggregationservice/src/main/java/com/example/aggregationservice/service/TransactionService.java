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
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final JdbcTemplate jdbcTemplate;

    private static final int BATCH_SIZE = 100;
    private static final String INSERT_SQL = """
        INSERT INTO transactions (
            id, account_id, user_id, bank_client_id, external_transaction_id,
            booking_date, amount, absolute_amount, is_expense,
            transaction_information, credit_debit_indicator, category
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (account_id, external_transaction_id) DO NOTHING
    """;

    @Transactional
    public int exportHistoricalTransactions(String bankClientId, int weeks) {
        LocalDateTime toDate = LocalDateTime.now();
        LocalDateTime fromDate = toDate.minusWeeks(weeks);
        return exportTransactionsForPeriod(bankClientId, fromDate, toDate);
    }

    @Transactional
    public int exportTransactionsForPeriod(String bankClientId, LocalDateTime fromDate, LocalDateTime toDate) {
        // ✅ Оптимизация: один запрос для аккаунтов и консентов
        List<Account> accounts = accountRepository.findActiveAccountsByBankClientId(bankClientId);
        List<UserConsent> activeConsents = userConsentRepository.findByBankClientIdAndStatus(
                bankClientId, ConsentStatus.ACTIVE);

        if (accounts.isEmpty() || activeConsents.isEmpty()) {
            log.info("No active accounts/consents for: {}", bankClientId);
            return 0;
        }

        // ✅ Кэшируем банки и консенты
        Map<UUID, Bank> bankCache = new HashMap<>();
        Map<UUID, String> consentCache = new HashMap<>();

        for (UserConsent consent : activeConsents) {
            try {
                bankCache.putIfAbsent(consent.getBankId(),
                        bankRepository.findById(consent.getBankId()).orElse(null));
                consentCache.put(consent.getId(), encryptionService.decrypt(consent.getConsentId()));
            } catch (Exception e) {
                log.warn("Failed to process consent {}: {}", consent.getId(), e.getMessage());
            }
        }

        int totalTransactions = 0;

        for (Account account : accounts) {
            UserConsent consent = findConsentForAccount(account, activeConsents);
            if (consent == null) continue;

            Bank bank = bankCache.get(consent.getBankId());
            String decryptedConsentId = consentCache.get(consent.getId());

            if (bank == null || decryptedConsentId == null) continue;

            try {
                List<Transaction> transactions = bankApiClient.fetchAccountTransactions(
                        bank, decryptedConsentId,
                        account.getExternalAccountId(), fromDate, toDate
                );

                if (!transactions.isEmpty()) {
                    int savedCount = saveTransactionsBatch(account.getId(), bankClientId, transactions);
                    totalTransactions += savedCount;
                    log.debug("Saved {} transactions for account {}", savedCount, account.getId());
                }

            } catch (Exception e) {
                log.error("Failed to export transactions for account {}: {}", account.getId(), e.getMessage());
            }
        }

        log.info("Exported {} total transactions for bankClientId: {}", totalTransactions, bankClientId);
        return totalTransactions;
    }

    private UserConsent findConsentForAccount(Account account, List<UserConsent> consents) {
        return consents.stream()
                .filter(c -> c.getId().equals(account.getUserConsentId()))
                .findFirst()
                .orElse(null);
    }

    private int saveTransactionsBatch(UUID accountId, String bankClientId, List<Transaction> transactions) {
        UUID userId = getUserIdByBankClientId(bankClientId);
        if (userId == null) {
            log.warn("Cannot save transactions: user not found for bankClientId: {}", bankClientId);
            return 0;
        }

        // ✅ Пакетная проверка дубликатов
        Set<String> existingIds = findExistingTransactionIds(accountId, transactions);

        // ✅ Подготовка данных для вставки
        List<Transaction> newTransactions = prepareTransactionsForInsert(
                accountId, userId, bankClientId, transactions, existingIds
        );

        if (newTransactions.isEmpty()) {
            return 0;
        }

        // ✅ Нативный batch insert
        return executeBatchInsert(newTransactions);
    }

    private Set<String> findExistingTransactionIds(UUID accountId, List<Transaction> transactions) {
        if (transactions.isEmpty()) return Collections.emptySet();

        List<String> externalIds = transactions.stream()
                .map(Transaction::getExternalTransactionId)
                .collect(Collectors.toList());

        // ✅ Один запрос для проверки всех ID
        return new HashSet<>(transactionRepository.findExistingExternalIds(accountId, externalIds));
    }

    private List<Transaction> prepareTransactionsForInsert(UUID accountId, UUID userId, String bankClientId,
                                                           List<Transaction> transactions, Set<String> existingIds) {
        return transactions.stream()
                .filter(t -> !existingIds.contains(t.getExternalTransactionId()))
                .peek(t -> {
                    t.setAccountId(accountId);
                    t.setUserId(userId);
                    t.setBankClientId(bankClientId);

                    if (t.getAbsoluteAmount() == null || t.getAbsoluteAmount().signum() == 0) {
                        t.setAbsoluteAmount(t.getAmount().abs());
                    }

                    if (t.getIsExpense() == null) {
                        t.setIsExpense("Debit".equalsIgnoreCase(t.getCreditDebitIndicator()));
                    }
                })
                .collect(Collectors.toList());
    }

    private int executeBatchInsert(List<Transaction> transactions) {
        int totalInserted = 0;

        for (int i = 0; i < transactions.size(); i += BATCH_SIZE) {
            List<Transaction> batch = transactions.subList(i,
                    Math.min(i + BATCH_SIZE, transactions.size()));

            int[] results = jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int j) throws SQLException {
                    Transaction t = batch.get(j);
                    setPreparedStatementValues(ps, t);
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });

            totalInserted += Arrays.stream(results).sum();
        }

        return totalInserted;
    }

    private void setPreparedStatementValues(PreparedStatement ps, Transaction t) throws SQLException {
        int paramIndex = 1;

        ps.setObject(paramIndex++, UUID.randomUUID()); // Генерируем UUID
        ps.setObject(paramIndex++, t.getAccountId());
        ps.setObject(paramIndex++, t.getUserId());
        ps.setString(paramIndex++, t.getBankClientId());
        ps.setString(paramIndex++, t.getExternalTransactionId());
        ps.setTimestamp(paramIndex++, Timestamp.valueOf(t.getBookingDate()));
        ps.setBigDecimal(paramIndex++, t.getAmount());
        ps.setBigDecimal(paramIndex++, t.getAbsoluteAmount());
        ps.setBoolean(paramIndex++, t.getIsExpense());
        ps.setString(paramIndex++, t.getTransactionInformation());
        ps.setString(paramIndex++, t.getCreditDebitIndicator());
        ps.setString(paramIndex++, t.getCategory());
    }

    private UUID getUserIdByBankClientId(String bankClientId) {
        try {
            ResponseEntity<UUID> response = userServiceClient.getUserIdByBankClientId(bankClientId);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get userId for bankClientId {}: {}", bankClientId, e.getMessage());
        }
        return null;
    }
}