package com.example.userservice.config;

import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final AuthService authService;
    private final UserRepository userRepository;

    @Override
    public void run(String... args) {

        try {
            // Даем время для полной инициализации Spring контекста
            Thread.sleep(5000);

            // Простая проверка - посчитаем пользователей
            long userCount = userRepository.count();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        int createdUsers = 0;
        for (int i = 1; i <= 9; i++) { // Сначала создадим только 3 для теста
            String email = i + "@gmail.com";

            try {
                if (userRepository.existsByEmail(email)) {
                    continue;
                }

                RegisterRequest request = RegisterRequest.builder()
                        .email(email)
                        .password("123123")
                        .fullName("Demo User " + i)
                        .phone("+7(888)888888" + i)
                        .clientId("team214-" + i)
                        .build();

                authService.register(request);
                createdUsers++;

            } catch (Exception e) {
                // Выведем более детальную информацию об ошибке
                if (e.getCause() != null) {
                    log.error("Root cause: {}", e.getCause().getMessage());
                }
            }
        }

        log.info("Total users in database now: {}", userRepository.count());
    }
}