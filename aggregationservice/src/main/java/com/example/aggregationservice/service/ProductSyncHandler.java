package com.example.aggregationservice.service;

import com.example.aggregationservice.model.ScheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSyncHandler implements TaskHandler {

    private final ProductSyncService productSyncService;

    @Override
    public String getSupportedTaskType() {
        return "PRODUCT_SYNC";
    }

    @Override
    public void handle(ScheduledTask task) {
        try {
            Map<String, Object> taskData = task.getTaskData();

            // Если указан конкретный банк
            if (taskData != null && taskData.containsKey("bankId")) {
                String rawBankId = String.valueOf(taskData.get("bankId"));
                UUID bankId = UUID.fromString(rawBankId);

                log.info("Executing PRODUCT_SYNC for bankId={}", bankId);
                productSyncService.updateProductsForBankId(bankId);

            } else {
                // Если нет bankId – обновляем все банки
                log.info("Executing PRODUCT_SYNC for ALL banks");
                productSyncService.updateAllBankProducts();
            }

        } catch (Exception e) {
            log.error("Failed to process product sync task: {}", e.getMessage(), e);
            throw new RuntimeException("Product sync task failed", e);
        }
    }

    @Override
    public boolean shouldDeleteAfterSuccess() {
        return false; // оставляем задачи как у balance handler
    }
}
