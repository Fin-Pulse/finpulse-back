package com.example.aggregationservice.config;

import com.example.aggregationservice.model.enums.TimeGroup;
import com.example.aggregationservice.service.TaskSchedulerService;
import com.example.aggregationservice.service.UserGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskInitializer {

    private final TaskSchedulerService taskSchedulerService;
    private final UserGroupService userGroupService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeTasks() {
        try {
            log.info("🚀 Initializing all scheduled tasks...");

            // 🔥 Сначала обновляем кэш пользователей
            userGroupService.refreshUserGroupsCache();

            // 🔥 1. Еженедельное обновление балансов - следующее воскресенье в 2:00
            Instant nextSunday2AM = calculateNextSunday2AM();
            taskSchedulerService.scheduleTask(
                    "BALANCE_UPDATE",
                    "WEEKLY_BALANCE_UPDATE",
                    Map.of("scope", "ALL_USERS"),
                    nextSunday2AM
            );
            log.info("📅 Scheduled weekly balance update for {}", nextSunday2AM);

            // 🔥 2. Ежедневная выгрузка транзакций по временным группам
            scheduleTransactionExportTasks();

            // 🔥 3. Еженедельный ML анализ - воскресенье в 23:00
            Instant nextSunday11PM = calculateNextSunday11PM();
            taskSchedulerService.scheduleTask(
                    "ML_ANALYSIS",
                    "WEEKLY_ML_ANALYSIS",
                    Map.of("analysisType", "WEEKLY_FORECAST"),
                    nextSunday11PM
            );
            log.info("📅 Scheduled weekly ML analysis for {}", nextSunday11PM);

            log.info("🎉 All scheduled tasks initialized successfully");

        } catch (Exception e) {
            log.error("❌ Failed to initialize scheduled tasks", e);
        }
    }

    /**
     * 🔥 Планирует 4 задачи выгрузки транзакций для временных групп
     */
    private void scheduleTransactionExportTasks() {
        scheduleGroupTask(TimeGroup.GROUP_00_06, 2, 30);  // 02:30
        scheduleGroupTask(TimeGroup.GROUP_06_12, 6, 30);  // 06:30
        scheduleGroupTask(TimeGroup.GROUP_12_18, 12, 30); // 12:30
        scheduleGroupTask(TimeGroup.GROUP_18_00, 18, 30); // 18:30
    }

    private void scheduleGroupTask(TimeGroup timeGroup, int hour, int minute) {
        Map<String, Object> taskData = Map.of(
                "groupCode", timeGroup.name(),
                "description", "Daily transaction export for " + timeGroup.getCode()
        );

        Instant firstExecution = getNextExecutionTime(hour, minute);

        taskSchedulerService.scheduleTask(
                "TRANSACTION_EXPORT",
                "DAILY_EXPORT_" + timeGroup.getCode(),
                taskData,
                firstExecution
        );

        log.info("📅 Scheduled {} export for {}:{}", timeGroup.getCode(), hour, minute);
    }

    /**
     * 🔥 Рассчитывает время следующего выполнения в указанный час и минуту
     */
    private Instant getNextExecutionTime(int hour, int minute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0);

        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }

        return nextRun.atZone(ZoneId.systemDefault()).toInstant();
    }

    /**
     * 🔥 Рассчитывает следующее воскресенье в 02:00
     */
    private Instant calculateNextSunday2AM() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextSunday = now.with(DayOfWeek.SUNDAY).withHour(2).withMinute(0).withSecond(0);

        if (now.isAfter(nextSunday)) {
            nextSunday = nextSunday.plusWeeks(1);
        }

        return nextSunday.atZone(ZoneId.systemDefault()).toInstant();
    }

    /**
     * 🔥 Рассчитывает следующее воскресенье в 23:00
     */
    private Instant calculateNextSunday11PM() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextSunday = now.with(DayOfWeek.SUNDAY).withHour(23).withMinute(0).withSecond(0);

        if (now.isAfter(nextSunday)) {
            nextSunday = nextSunday.plusWeeks(1);
        }

        return nextSunday.atZone(ZoneId.systemDefault()).toInstant();
    }
}