package com.example.aggregationservice.service;

import com.example.aggregationservice.client.NotificationServiceClient;
import com.example.aggregationservice.client.UserServiceClient;
import com.example.aggregationservice.dto.CreateNotificationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationServiceClient notificationServiceClient;
    private final UserServiceClient userServiceClient;

    public void sendAccountsLoadedNotification(String bankClientId, String bankCode, int accountsCount) {
        try {
            UUID userId = getUserIdFromUserService(bankClientId);
            if (userId == null) return;

            CreateNotificationRequest request = new CreateNotificationRequest();
            request.setUserId(userId);
            request.setType("WEEKLY_FORECAST_READY");
            request.setTitle("Счета успешно загружены");
            request.setMessage(String.format("Из банка %s загружено %d счетов", bankCode, accountsCount));
            notificationServiceClient.createNotification(request);

        } catch (Exception e) {
            log.error("Failed to send accounts loaded notification: {}", e.getMessage());
        }
    }

    private UUID getUserIdFromUserService(String bankClientId) {
        try {
            ResponseEntity<UUID> response = userServiceClient.getUserIdByBankClientId(bankClientId);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.warn("User not found for bankClientId: {}", bankClientId);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to get userId for {}: {}", bankClientId, e.getMessage());
            return null;
        }
    }
}