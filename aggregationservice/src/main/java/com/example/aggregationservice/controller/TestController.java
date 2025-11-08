package com.example.aggregationservice.controller;

import com.example.aggregationservice.client.UserServiceClient;
import com.example.aggregationservice.dto.UserForecastUpdateEvent;
import com.example.aggregationservice.model.ScheduledTask;
import com.example.aggregationservice.model.enums.TimeGroup;
import com.example.aggregationservice.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
public class TestController {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserGroupService userGroupService;
    private final TaskSchedulerService taskSchedulerService;
    private final TransactionExportHandler transactionExportHandler;
    private final MlAnalysisHandler mlAnalysisHandler;
    private final UserServiceClient userServiceClient;

    /**
     * 🔥 Тест Kafka - отправка тестового события
     */
    @PostMapping("/kafka/send-test-event")
    public ResponseEntity<String> sendTestKafkaEvent(@RequestParam(defaultValue = "test-user") String userId) {
        try {
            UserForecastUpdateEvent event = UserForecastUpdateEvent.builder()
                    .userId(UUID.fromString(userId))
                    .bankClientId("test-bank-client-" + System.currentTimeMillis())
                    .analysisType("TEST")
                    .timestamp(System.currentTimeMillis())
                    .build();

            kafkaTemplate.send("user_forecast_update", userId, event);

            log.info("✅ Test Kafka event sent for user: {}", userId);
            return ResponseEntity.ok("Test event sent to Kafka for user: " + userId);

        } catch (Exception e) {
            log.error("❌ Failed to send test Kafka event", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * 🔥 Тест Redis - получение пользователей по группе
     */
    @GetMapping("/redis/user-groups/{groupCode}")
    public ResponseEntity<List<UUID>> getUserGroup(@PathVariable String groupCode) {
        try {
            TimeGroup timeGroup = TimeGroup.valueOf(groupCode);
            List<UUID> userIds = userGroupService.getUserIdsByTimeGroup(timeGroup);

            log.info("✅ Retrieved {} users for group: {}", userIds.size(), groupCode);
            return ResponseEntity.ok(userIds);

        } catch (Exception e) {
            log.error("❌ Failed to get user group: {}", groupCode, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 🔥 Тест Redis - обновление кэша
     */
    @PostMapping("/redis/refresh-cache")
    public ResponseEntity<String> refreshUserCache() {
        try {
            userGroupService.refreshUserGroupsCache();
            return ResponseEntity.ok("User cache refreshed successfully");
        } catch (Exception e) {
            log.error("❌ Failed to refresh user cache", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * 🔥 Тест выгрузки транзакций для конкретного пользователя
     */
    @PostMapping("/transactions/export-user/{userId}")
    public ResponseEntity<String> testExportUserTransactions(@PathVariable UUID userId) {
        try {
            // Создаем mock задачу для обработчика
            var mockTask = new Object() {
                public Map<String, Object> getTaskData() {
                    return Map.of("groupCode", "GROUP_00_06");
                }
            };

            // Запускаем выгрузку для конкретного пользователя
            transactionExportHandler.exportUserTransactions(userId);

            return ResponseEntity.ok("Transaction export started for user: " + userId);

        } catch (Exception e) {
            log.error("❌ Failed to export transactions for user: {}", userId, e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * 🔥 Тест ML анализа для конкретного пользователя
     */
    @PostMapping("/ml/analyze-user/{userId}")
    public ResponseEntity<String> testMlAnalysisForUser(@PathVariable UUID userId) {
        try {
            String bankClientId = userServiceClient.getBankClientId(userId);
            if (bankClientId == null) {
                return ResponseEntity.badRequest().body("No bankClientId found for user: " + userId);
            }

            UserForecastUpdateEvent event = UserForecastUpdateEvent.builder()
                    .userId(userId)
                    .bankClientId(bankClientId)
                    .analysisType("TEST_ANALYSIS")
                    .timestamp(System.currentTimeMillis())
                    .build();

            mlAnalysisHandler.sendToKafka(userId, event);

            return ResponseEntity.ok("ML analysis sent for user: " + userId);

        } catch (Exception e) {
            log.error("❌ Failed to send ML analysis for user: {}", userId, e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * 🔥 Тест создания scheduled task
     */
    @PostMapping("/tasks/create-test-task")
    public ResponseEntity<String> createTestTask(@RequestParam String taskType,
                                                 @RequestParam String taskName) {
        try {
            taskSchedulerService.scheduleTask(
                    taskType,
                    taskName,
                    Map.of("testData", "value", "timestamp", System.currentTimeMillis()),
                    java.time.Instant.now().plusSeconds(30) // через 30 секунд
            );

            return ResponseEntity.ok("Test task created: " + taskName);

        } catch (Exception e) {
            log.error("❌ Failed to create test task", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * 🔥 Получение информации о системе
     */
    @GetMapping("/system/info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        try {
            List<UUID> allUsers = userGroupService.getAllActiveUserIds();

            Map<String, Object> info = Map.of(
                    "timestamp", LocalDateTime.now().toString(),
                    "totalUsers", allUsers.size(),
                    "userGroups", Map.of(
                            "GROUP_00_06", userGroupService.getUserIdsByTimeGroup(TimeGroup.GROUP_00_06).size(),
                            "GROUP_06_12", userGroupService.getUserIdsByTimeGroup(TimeGroup.GROUP_06_12).size(),
                            "GROUP_12_18", userGroupService.getUserIdsByTimeGroup(TimeGroup.GROUP_12_18).size(),
                            "GROUP_18_00", userGroupService.getUserIdsByTimeGroup(TimeGroup.GROUP_18_00).size()
                    ),
                    "kafkaEnabled", true,
                    "redisEnabled", true
            );

            return ResponseEntity.ok(info);

        } catch (Exception e) {
            log.error("❌ Failed to get system info", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 🔥 Принудительный запуск ML анализа для всех пользователей
     */
    @PostMapping("/ml/analyze-all")
    public ResponseEntity<String> triggerMlAnalysisForAll() {
        try {
            Object mockTask = new Object() {
                public Map<String, Object> getTaskData() {
                    return Map.of();
                }
            };

            mlAnalysisHandler.handle((ScheduledTask) mockTask);

            return ResponseEntity.ok("ML analysis started for all users");

        } catch (Exception e) {
            log.error("❌ Failed to start ML analysis", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}