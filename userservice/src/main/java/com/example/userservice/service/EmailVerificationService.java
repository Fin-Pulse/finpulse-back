package com.example.userservice.service;

import com.example.userservice.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailVerificationService {

    public void sendVerificationEmail(User user) {
        // В реальном приложении здесь будет отправка email
        // В хакатоне просто логируем ссылку для верификации
        String verificationUrl = String.format(
                "http://localhost:8081/api/bank/auth/verify/%s",
                user.getVerificationToken()
        );

        log.info("=== EMAIL VERIFICATION ===");
        log.info("To: {}", user.getEmail());
        log.info("Verification URL: {}", verificationUrl);
        log.info("=== END ===");
    }
}