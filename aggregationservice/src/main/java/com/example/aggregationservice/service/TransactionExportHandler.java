// service/TransactionExportHandler.java
package com.example.aggregationservice.service;

import com.example.aggregationservice.client.UserServiceClient;
import com.example.aggregationservice.model.enums.TimeGroup;
import com.example.aggregationservice.model.Account;
import com.example.aggregationservice.model.ScheduledTask;
import com.example.aggregationservice.model.Transaction;
import com.example.aggregationservice.repository.AccountRepository;
import com.example.aggregationservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionExportHandler implements TaskHandler {

    private final UserGroupService userGroupService;
    private final UserServiceClient userServiceClient;
    private final BankApiClient bankApiClient;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Override
    public String getSupportedTaskType() {
        return "TRANSACTION_EXPORT";
    }

    @Override
    public void handle(ScheduledTask task) {
        Map<String, Object> taskData = task.getTaskData();
        String groupCode = (String) taskData.get("groupCode");
        TimeGroup timeGroup = TimeGroup.valueOf(groupCode);

        log.info("🚀 Starting transaction export for group: {}", timeGroup.getCode());

        List<UUID> userIds = userGroupService.getUserIdsByTimeGroup(timeGroup);
        log.info("📊 Processing {} users", userIds.size());

        int successCount = 0;

        for (UUID userId : userIds) {
            try {
                exportUserTransactions(userId);
                successCount++;

                if (successCount % 10 == 0) {
                    log.info("📈 Progress: {}/{}", successCount, userIds.size());
                }

            } catch (Exception e) {
                log.error("❌ Failed for user: {}", userId, e);
            }
        }

        log.info("🎉 Export completed: {} success", successCount);
    }

    public void exportUserTransactions(UUID userId) {
        // Получаем bankClientId для пользователя
        String bankClientId = userServiceClient.getBankClientId(userId);
        if (bankClientId == null) {
            log.warn("⚠️ No bankClientId for user: {}", userId);
            return;
        }

        // Получаем активные счета пользователя используя твой существующий метод
        List<Account> accounts = accountRepository.findActiveAccountsByBankClientId(bankClientId);

        if (accounts.isEmpty()) {
            log.debug("👤 User {} has no active accounts", userId);
            return;
        }

        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        LocalDateTime today = LocalDateTime.now();

        int totalTransactions = 0;

        for (Account account : accounts) {
            try {
                // Выгружаем транзакции через Bank API
                List<Transaction> transactions = bankApiClient.getAccountTransactions(
                        bankClientId,
                        account.getExternalAccountId(),
                        yesterday,
                        today
                );

                // Сохраняем транзакции с bankClientId
                int savedCount = saveTransactions(account.getId(), bankClientId, transactions);
                totalTransactions += savedCount;

                log.debug("✅ Account {}: saved {}/{} transactions",
                        account.getId(), savedCount, transactions.size());

            } catch (Exception e) {
                log.error("❌ Failed for account: {}", account.getId(), e);
            }
        }

        log.info("✅ User {} processed: {} accounts, {} transactions",
                userId, accounts.size(), totalTransactions);
    }

    /**
     * Сохраняет транзакции с проверкой на дубликаты
     */
    private int saveTransactions(UUID accountId, String bankClientId, List<Transaction> transactions) {
        int savedCount = 0;

        for (Transaction transaction : transactions) {
            try {
                // Проверяем, не существует ли уже такая транзакция
                if (!transactionRepository.existsByAccountIdAndExternalTransactionId(
                        accountId, transaction.getExternalTransactionId())) {

                    transaction.setAccountId(accountId);
                    transaction.setBankClientId(bankClientId); // 🔥 УСТАНАВЛИВАЕМ bankClientId
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

    @Override
    public boolean shouldDeleteAfterSuccess() {
        return false;
    }
}