package com.example.aggregationservice.config;

import com.example.aggregationservice.service.TaskSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskInitializer {

    private final TaskSchedulerService taskSchedulerService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeTasks() {
        try {
            // Еженедельное обновление балансов - следующее воскресенье в 2:00
            Instant nextSunday = calculateNextSunday2AM();

            taskSchedulerService.scheduleTask(
                    "BALANCE_UPDATE",
                    "WEEKLY_BALANCE_UPDATE",
                    Map.of("scope", "ALL_USERS"),
                    nextSunday
            );

            log.info("🎉 Scheduled tasks initialized");
        } catch (Exception e) {
            log.error("Failed to initialize scheduled tasks", e);
        }
    }

    private Instant calculateNextSunday2AM() {
        // Для теста - через 1 день, в продакшене - логика расчета воскресенья
        return Instant.now().plus(1, ChronoUnit.DAYS);
    }
}