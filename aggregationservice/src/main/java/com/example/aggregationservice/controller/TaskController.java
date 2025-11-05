package com.example.aggregationservice.controller;

import com.example.aggregationservice.service.TaskSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskSchedulerService taskSchedulerService;

    @PostMapping("/balance-update")
    public ResponseEntity<String> scheduleBalanceUpdate(@RequestBody Map<String, Object> request) {
        String clientId = (String) request.get("clientId");
        Instant scheduledTime = Instant.now().plusSeconds(30); // через 30 секунд

        Map<String, Object> taskData = Map.of("clientId", clientId != null ? clientId : "ALL");

        taskSchedulerService.scheduleTask(
                "BALANCE_UPDATE",
                "MANUAL_BALANCE_UPDATE",
                taskData,
                scheduledTime
        );

        return ResponseEntity.ok("Balance update scheduled");
    }
}