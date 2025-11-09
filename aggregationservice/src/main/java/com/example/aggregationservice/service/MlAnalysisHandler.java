package com.example.aggregationservice.service;

import com.example.aggregationservice.client.UserServiceClient;
import com.example.aggregationservice.dto.UserForecastUpdateEvent;
import com.example.aggregationservice.model.ScheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MlAnalysisHandler implements TaskHandler {

    private final UserGroupService userGroupService;
    private final UserServiceClient userServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public String getSupportedTaskType() {
        return "ML_ANALYSIS";
    }

    @Override
    public void handle(ScheduledTask task) {
        List<UUID> allUserIds = userGroupService.getAllActiveUserIds();

        int sentCount = 0;

        for (UUID userId : allUserIds) {
            try {
                String bankClientId = userServiceClient.getBankClientId(userId);
                if (bankClientId == null) {
                    log.warn("No bankClientId for user: {}", userId);
                    continue;
                }

                UserForecastUpdateEvent event = UserForecastUpdateEvent.builder()
                        .userId(userId)
                        .bankClientId(bankClientId)
                        .analysisType("WEEKLY_FORECAST")
                        .timestamp(System.currentTimeMillis())
                        .build();

                sendToKafka(userId, event);
                sentCount++;

            } catch (Exception e) {
                log.error("Failed to process user {} for ML analysis", userId, e);
            }
        }
    }

    public void sendToKafka(UUID userId, UserForecastUpdateEvent event) {
        try {
            int partition = Math.abs(userId.hashCode()) % 10;
            kafkaTemplate.send("user_forecast_update", partition, userId.toString(), event);
        } catch (Exception e) {
            log.error("Failed to send ML event for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public boolean shouldDeleteAfterSuccess() {
        return false;
    }
}