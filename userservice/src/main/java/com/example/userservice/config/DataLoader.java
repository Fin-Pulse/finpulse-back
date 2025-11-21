package com.example.userservice.config;

import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final AuthService authService;
    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        log.info("Starting demo data initialization...");

        for (int i = 1; i <= 9; i++) {
            String email = i + "@gmail.com";

            if (userRepository.existsByEmail(email)) {
                log.info("Demo user {} already exists, skipping", email);
                continue;
            }

            try {
                RegisterRequest request = RegisterRequest.builder()
                        .email(email)
                        .password("123123")
                        .fullName("Demo User " + i)
                        .phone("+7(888)888888" + i)
                        .clientId("team214-" + i)
                        .build();

                authService.register(request);
                log.info("Successfully registered demo user: {}", email);

            } catch (Exception e) {
                log.error("Failed to register demo user {}: {}", email, e.getMessage());
                e.printStackTrace();
            }
        }
        log.info("Demo data initialization completed");
    }
}

