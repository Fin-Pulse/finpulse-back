package com.example.aggregationservice.service;

import com.example.aggregationservice.client.UserServiceClient;
import com.example.aggregationservice.dto.UserForecastUpdateEvent;
import com.example.aggregationservice.model.Account;
import com.example.aggregationservice.model.enums.TimeGroup;
import com.example.aggregationservice.model.ScheduledTask;
import com.example.aggregationservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final TransactionService transactionService;
    private final AccountRepository accountRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public String getSupportedTaskType() {
        return "TRANSACTION_EXPORT";
    }

    @Override
    public void handle(ScheduledTask task) {
        Map<String, Object> taskData = task.getTaskData();
        String groupCode = (String) taskData.get("groupCode");
        TimeGroup timeGroup = TimeGroup.valueOf(groupCode);

        List<UUID> userIds = userGroupService.getUserIdsByTimeGroup(timeGroup);

        int successCount = 0;

        for (UUID userId : userIds) {
            try {
                exportUserTransactions(userId);
                successCount++;

            } catch (Exception e) {
                log.error("Failed for user: {}", userId, e);
            }
        }
    }

    public void exportUserTransactions(UUID userId) {
        String bankClientId = userServiceClient.getBankClientId(userId);
        if (bankClientId == null) {
            log.warn("No bankClientId for user: {}", userId);
            return;
        }

        List<Account> accounts = accountRepository.findActiveAccountsByBankClientId(bankClientId);
        if (accounts.isEmpty()) {
            return;
        }

        try {
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            LocalDateTime today = LocalDateTime.now();

            int transactionsCount = transactionService.exportTransactionsForPeriod(
                    bankClientId, yesterday, today);

            if (transactionsCount > 0) {
                sendToMlService(userId, bankClientId, "DAILY_UPDATE", accounts.size());
            }

        } catch (Exception e) {
            log.error("Failed to export transactions for user: {}", userId, e);
        }
    }

    private void sendToMlService(UUID userId, String bankClientId, String analysisType, int accountsCount) {
        try {
            UserForecastUpdateEvent event = UserForecastUpdateEvent.builder()
                    .userId(userId)
                    .bankClientId(bankClientId)
                    .analysisType(analysisType)
                    .timestamp(System.currentTimeMillis())
                    .build();

            int partition = Math.abs(userId.hashCode()) % 10;
            kafkaTemplate.send("user_forecast_update", partition, userId.toString(), event);

        } catch (Exception e) {
            log.error("Failed to send ML update for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public boolean shouldDeleteAfterSuccess() {
        return false;
    }
}