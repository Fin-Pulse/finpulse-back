package com.example.aggregationservice.scheduler;

import com.example.aggregationservice.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProcessor {

    private final TaskSchedulerService taskSchedulerService;
    private final BalanceUpdateHandler balanceUpdateHandler;
    private final BankConsentHandler bankConsentHandler;
    private final TransactionExportHandler transactionExportHandler;
    private final ProductSyncHandler productSyncHandler;
    private final MlAnalysisHandler mlAnalysisHandler;


    @Scheduled(fixedRate = 10000)
    public void processTasks() {
        try {
            taskSchedulerService.processDueTasks(balanceUpdateHandler);
            taskSchedulerService.processDueTasks(bankConsentHandler);
            taskSchedulerService.processDueTasks(transactionExportHandler);
            taskSchedulerService.processDueTasks(productSyncHandler);
            taskSchedulerService.processDueTasks(mlAnalysisHandler);

        } catch (Exception e) {
            log.error("Task processing failed", e);
        }
    }
}