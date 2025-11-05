package com.example.aggregationservice.service;

import com.example.aggregationservice.model.ScheduledTask;
import com.example.aggregationservice.repository.ScheduledTaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSchedulerService {

    private final ScheduledTaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    private final String instanceId = UUID.randomUUID().toString();

    @Transactional
    public ScheduledTask scheduleTask(String taskType, String taskName, Map<String, Object> taskData, Instant scheduledTime) {
        try {


            ScheduledTask task = new ScheduledTask();
            task.setTaskType(taskType);
            task.setTaskName(taskName);
            task.setTaskData(taskData);
            task.setScheduledTime(scheduledTime);

            ScheduledTask saved = taskRepository.save(task);
            log.info("✅ Scheduled task: {} [{}] for {}", taskName, taskType, scheduledTime);

            return saved;
        } catch (Exception e) {
            log.error("Failed to serialize task data", e);
            throw new RuntimeException("Task scheduling failed", e);
        }
    }

    @Transactional
    public void processDueTasks(TaskHandler handler) {
        Instant now = Instant.now();
        List<ScheduledTask> dueTasks = taskRepository.findDueTasks(now);

        log.info("🔍 Found {} due tasks to process", dueTasks.size());

        for (ScheduledTask task : dueTasks) {
            if (!task.getTaskType().equals(handler.getSupportedTaskType())) {
                continue;
            }

            int locked = taskRepository.lockTask(task.getId(), instanceId, now);
            if (locked > 0) {
                try {
                    log.info("🎯 Executing task: {} [{}]", task.getTaskName(), task.getTaskType());
                    handler.handle(task);

                    task.setStatus("COMPLETED");
                    taskRepository.save(task);
                    log.info("✅ Task completed: {}", task.getTaskName());

                } catch (Exception e) {
                    log.error("❌ Task failed: {}", task.getTaskName(), e);
                    handleTaskFailure(task, e);
                }
            }
        }
    }

    private void handleTaskFailure(ScheduledTask task, Exception e) {
        task.setRetryCount(task.getRetryCount() + 1);

        if (task.getRetryCount() >= task.getMaxRetries()) {
            task.setStatus("FAILED");
            task.setLastError(e.getMessage());
        } else {
            Instant retryTime = Instant.now().plusSeconds(300 * task.getRetryCount());
            task.setScheduledTime(retryTime);
            task.setStatus("PENDING");
            task.setLockedBy(null);
            task.setLockedAt(null);
        }

        taskRepository.save(task);
    }
}