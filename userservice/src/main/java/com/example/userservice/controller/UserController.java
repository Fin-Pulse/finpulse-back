package com.example.userservice.controller;

import com.example.userservice.dto.UserProfile;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/bank/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "API для работы с пользователями")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    @Operation(summary = "Получить профиль", description = "Возвращает данные текущего пользователя")
    public ResponseEntity<UserProfile> getCurrentUser(
            @Parameter(hidden = true)
            @RequestHeader("X-User-Id") UUID userId) {

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        UserProfile profile = userService.getUserProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/by-bank-client-id/{bankClientId}")
    public ResponseEntity<UUID> getUserIdByBankClientId(@PathVariable String bankClientId) {

        return userRepository.findByBankClientId(bankClientId)
                .map(user -> ResponseEntity.ok(user.getId()))
                .orElseGet(() -> {
                    log.warn("User not found for bankClientId: {}", bankClientId);
                    return ResponseEntity.notFound().build();
                });
    }

    @GetMapping("/active-ids")
    @Operation(summary = "Get all active user IDs", description = "For internal use by other services")
    public ResponseEntity<List<UUID>> getAllActiveUserIds() {
        List<UUID> activeUserIds = userRepository.findActiveUserIds();
        return ResponseEntity.ok(activeUserIds);
    }

    @GetMapping("/{userId}/bank-client-id")
    public ResponseEntity<String> getBankClientId(@PathVariable UUID userId) {
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(user.getBankClientId()))
                .orElse(ResponseEntity.notFound().build());
    }
}
