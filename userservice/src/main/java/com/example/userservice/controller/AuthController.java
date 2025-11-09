package com.example.userservice.controller;

import com.example.userservice.dto.AuthRequest;
import com.example.userservice.dto.AuthResponse;
import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.service.AuthService;
import com.example.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bank/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "API для регистрации и аутентификации")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Регистрация пользователя", description = "Создает нового пользователя и возвращает JWT токен")
    public ResponseEntity<AuthResponse> register(
            @Parameter(description = "Данные для регистрации", required = true)
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Вход в систему", description = "Аутентифицирует пользователя и возвращает JWT токен")
    public ResponseEntity<AuthResponse> login(
            @Parameter(description = "Учетные данные", required = true)
            @Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify/{token}")
    @Operation(summary = "Подтверждение email", description = "Подтверждает email пользователя по токену")
    public ResponseEntity<Map<String, String>> verifyEmail(
            @Parameter(description = "Токен верификации", required = true)
            @PathVariable String token) {
        userService.verifyUser(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
    }
}