package com.example.userservice.controller;

import com.example.userservice.dto.UserProfile;
import com.example.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/bank/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "API для работы с пользователями")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Получить профиль", description = "Возвращает данные текущего пользователя")
    public ResponseEntity<UserProfile> getCurrentUser(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UUID userId) {
        UserProfile profile = userService.getUserProfile(userId);
        return ResponseEntity.ok(profile);
    }
}