package com.example.aggregationservice.scheduler;

import com.example.aggregationservice.service.BalanceUpdateHandler;
import com.example.aggregationservice.service.TaskSchedulerService;
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

    /**
     * Обрабатываем задачи каждую минуту
     */
    @Scheduled(fixedRate = 60000)
    public void processTasks() {
        try {
            taskSchedulerService.processDueTasks(balanceUpdateHandler);
        } catch (Exception e) {
            log.error("Task processing failed", e);
        }
    }
}