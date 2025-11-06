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
    private final ObjectMapper objectMapper;

    /**
     * ОТПРАВЛЯЕМ УВЕДОМЛЕНИЕ О ЗАГРУЗКЕ СЧЕТОВ
     */
    public void sendAccountsLoadedNotification(String bankClientId, String bankCode, int accountsCount) {
        try {
            // 🔥 1. ПОЛУЧАЕМ userId ИЗ UserService
            UUID userId = getUserIdFromUserService(bankClientId);
            if (userId == null) return;

            // 🔥 2. ОТПРАВЛЯЕМ УВЕДОМЛЕНИЕ
            CreateNotificationRequest request = new CreateNotificationRequest();
            request.setUserId(userId);
            request.setType("WEEKLY_FORECAST_READY");
            request.setTitle("🎉 Счета успешно загружены");
            request.setMessage(String.format("Из банка %s загружено %d счетов", bankCode, accountsCount));

            log.info("📤 Sending notification request: {}",
                    objectMapper.writeValueAsString(request));
            notificationServiceClient.createNotification(request);
            log.info("📨 Sent accounts loaded notification for user {}", userId);

        } catch (Exception e) {
            log.error("❌ Failed to send accounts loaded notification: {}", e.getMessage());
        }
    }

    /**
     * ОТПРАВЛЯЕМ УВЕДОМЛЕНИЕ ОБ ОБНОВЛЕНИИ БАЛАНСОВ
     */
    public void sendBalancesUpdatedNotification(String bankClientId, int updatedAccountsCount) {
        try {
            // 🔥 1. ПОЛУЧАЕМ userId ИЗ UserService
            UUID userId = getUserIdFromUserService(bankClientId);
            if (userId == null) return;

            // 🔥 2. ОТПРАВЛЯЕМ УВЕДОМЛЕНИЕ
            CreateNotificationRequest request = new CreateNotificationRequest();
            request.setUserId(userId);
            request.setType("BALANCES_UPDATED");
            request.setTitle("💰 Балансы обновлены");
            request.setMessage(String.format("Балансы для %d счетов обновлены", updatedAccountsCount));

            notificationServiceClient.createNotification(request);
            log.info("✅ Notification sent successfully: {}",userId);

        } catch (Exception e) {
            log.error("❌ Failed to send balances updated notification: {}", e.getMessage());
        }
    }

    /**
     * ОТПРАВЛЯЕМ УВЕДОМЛЕНИЕ ОБ ОШИБКЕ
     */
    public void sendVerificationErrorNotification(String bankClientId, String bankName, String error) {
        try {
            // 🔥 1. ПОЛУЧАЕМ userId ИЗ UserService
            UUID userId = getUserIdFromUserService(bankClientId);
            if (userId == null) return;

            // 🔥 2. ОТПРАВЛЯЕМ УВЕДОМЛЕНИЕ
            CreateNotificationRequest request = new CreateNotificationRequest();
            request.setUserId(userId);
            request.setType("VERIFICATION_ERROR");
            request.setTitle("🚨 Ошибка подключения банка");
            request.setMessage(String.format("При подключении банка %s произошла ошибка: %s", bankName, error));

            notificationServiceClient.createNotification(request);
            log.info("📨 Sent error notification for user {}", userId);

        } catch (Exception e) {
            log.error("❌ Failed to send error notification: {}", e.getMessage());
        }
    }

    /**
     * 🔥 ВСПОМОГАТЕЛЬНЫЙ МЕТОД ДЛЯ ПОЛУЧЕНИЯ userId
     */
    private UUID getUserIdFromUserService(String bankClientId) {
        try {
            ResponseEntity<UUID> response = userServiceClient.getUserIdByBankClientId(bankClientId);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.warn("⚠️ User not found for bankClientId: {}", bankClientId);
                return null;
            }
        } catch (Exception e) {
            log.error("❌ Failed to get userId for {}: {}", bankClientId, e.getMessage());
            return null;
        }
    }
}